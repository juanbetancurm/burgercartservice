package com.rockburger.cartservice.domain.api.usecase;

import com.rockburger.cartservice.domain.api.ICartServicePort;
import com.rockburger.cartservice.domain.exception.*;
import com.rockburger.cartservice.domain.model.CartModel;
import com.rockburger.cartservice.domain.model.CartItemModel;
import com.rockburger.cartservice.domain.spi.ICartPersistencePort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.util.Optional;

public class CartUseCase implements ICartServicePort {
    private static final Logger logger = LoggerFactory.getLogger(CartUseCase.class);
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String ABANDONED_STATUS = "ABANDONED";
    private static final String COMPLETED_STATUS = "COMPLETED";

    // Cart session management constants
    private static final int CART_EXPIRY_HOURS = 24; // Cart expires after 24 hours
    private static final int CART_WARNING_HOURS = 4; // Warn when cart will expire in 4 hours
    private static final int MAX_RETRY_ATTEMPTS = 3; // Maximum retry attempts for optimistic locking
    private static final long RETRY_BASE_DELAY_MS = 100; // Base delay for retries

    private final ICartPersistencePort cartPersistencePort;

    public CartUseCase(ICartPersistencePort cartPersistencePort) {
        this.cartPersistencePort = cartPersistencePort;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    public CartModel createCart(String userId) {
        validateUserId(userId);
        logger.info("Creating new cart for user: {}", userId);

        try {
            // Use a more atomic approach to prevent race conditions
            return createCartWithAtomicCheck(userId);
        } catch (Exception e) {
            logger.error("Error creating new cart for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to create new cart", e);
        }
    }

    /**
     * Atomic cart creation to prevent race conditions
     */
    private CartModel createCartWithAtomicCheck(String userId) {
        // First, try to clean up any stale carts
        try {
            abandonStaleCartsForUser(userId);
        } catch (Exception e) {
            logger.warn("Failed to clean up stale carts for user {}: {}", userId, e.getMessage());
            // Continue with cart creation even if cleanup fails
        }

        // Check for existing active cart after cleanup
        Optional<CartModel> existingCart = cartPersistencePort.findByUserIdAndStatus(userId, ACTIVE_STATUS);
        if (existingCart.isPresent()) {
            CartModel cart = existingCart.get();

            if (isCartStale(cart)) {
                logger.info("Found stale cart for user {}, abandoning it", userId);
                try {
                    cart.abandon();
                    cartPersistencePort.save(cart);
                } catch (Exception e) {
                    logger.warn("Failed to abandon stale cart for user {}: {}", userId, e.getMessage());
                    // Create new cart anyway
                }
            } else {
                logger.info("User {} already has an active cart with ID {}", userId, cart.getId());
                return cart;
            }
        }

        // Create new cart
        CartModel newCart = new CartModel(userId);
        CartModel savedCart = cartPersistencePort.save(newCart);
        logger.info("Created new cart with ID {} for user {}", savedCart.getId(), userId);
        return savedCart;
    }

    @Override
    @Transactional(readOnly = true)
    public CartModel getActiveCart(String userId) {
        validateUserId(userId);
        logger.debug("Retrieving active cart for user: {}", userId);

        Optional<CartModel> cartOptional = cartPersistencePort.findByUserIdAndStatus(userId, ACTIVE_STATUS);

        if (cartOptional.isEmpty()) {
            logger.warn("No active cart found for user: {}", userId);
            throw new CartNotFoundException("No active cart found for user");
        }

        CartModel cart = cartOptional.get();

        // Check if cart is stale
        if (isCartStale(cart)) {
            logger.warn("Found stale cart for user {}, abandoning it", userId);
            try {
                cart.abandon();
                cartPersistencePort.save(cart);
            } catch (Exception e) {
                logger.error("Failed to abandon stale cart: {}", e.getMessage());
            }
            throw new CartNotFoundException("Cart has expired, please create a new cart");
        }

        // Log warning if cart is approaching expiry
        if (isCartApproachingExpiry(cart)) {
            logger.info("Cart for user {} is approaching expiry (last updated: {})",
                    userId, cart.getLastUpdated());
        }

        return cart;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartModel addItem(String userId, CartItemModel item) {
        validateUserId(userId);
        validateCartItem(item);

        logger.info("Adding item to cart for user: {} - Article ID: {}, Name: {}, Quantity: {}",
                userId, item.getArticleId(), item.getArticleName(), item.getQuantity());

        return executeWithRetry(
                () -> addItemWithRetry(userId, item),
                "add item to cart",
                userId
        );
    }

    /**
     * Execute operation with retry logic for optimistic locking failures
     */
    private <T> T executeWithRetry(SupplierWithException<T> operation, String operationName, String userId) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (ConcurrentCartModificationException e) {
                lastException = e;
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    logger.error("Failed to {} after {} attempts due to concurrent modification for user {}",
                            operationName, MAX_RETRY_ATTEMPTS, userId);
                    break;
                }

                logger.warn("Optimistic locking failure on attempt {} for user {} during {}, retrying...",
                        attempt, userId, operationName);

                try {
                    long delay = RETRY_BASE_DELAY_MS * attempt; // Progressive delay
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying cart operation", ie);
                }
            } catch (CartNotFoundException | CartItemNotFoundException | DuplicateArticleException e) {
                // These exceptions should not be retried
                throw e;
            } catch (Exception e) {
                logger.error("Error during {} for user {} on attempt {}: {}", operationName, userId, attempt, e.getMessage(), e);
                throw new RuntimeException("Failed to " + operationName + ": " + e.getMessage(), e);
            }
        }

        throw new RuntimeException("Unable to " + operationName + " due to concurrent modifications. Please try again.");
    }

    private CartModel addItemWithRetry(String userId, CartItemModel item) {
        logger.debug("Attempting to add item for user: {}", userId);

        // Get or create cart with proper synchronization
        CartModel cart = getOrCreateCartForOperation(userId);

        // Validate cart state before adding item
        validateCartForOperation(cart, userId);

        // Check if item already exists in cart
        boolean itemExists = cart.getItems().stream()
                .anyMatch(existingItem -> existingItem.getArticleId().equals(item.getArticleId()));

        if (itemExists) {
            logger.warn("Item with article ID {} already exists in cart for user {}", item.getArticleId(), userId);
            throw new DuplicateArticleException("Item already exists in cart. Use update quantity instead.");
        }

        // Add item to cart
        cart.addItem(item);

        // Save and verify the cart
        CartModel updatedCart = cartPersistencePort.save(cart);

        // Additional verification
        if (updatedCart.getItems().isEmpty()) {
            logger.error("CRITICAL: Cart appears empty after save operation for user {}. This indicates a persistence issue.", userId);
            throw new RuntimeException("Failed to persist cart items properly");
        }

        logger.info("Item successfully added to cart for user {}. Cart now has {} items",
                userId, updatedCart.getItems().size());

        return updatedCart;
    }

    /**
     * Get or create cart for operations with proper error handling
     */
    private CartModel getOrCreateCartForOperation(String userId) {
        try {
            return getActiveCartWithSessionValidation(userId);
        } catch (CartNotFoundException e) {
            logger.info("No active cart found for user {}, creating a new one", userId);
            return createCartForImmediateUse(userId);
        }
    }

    /**
     * Create a cart for immediate use with proper error handling
     */
    private CartModel createCartForImmediateUse(String userId) {
        try {
            // Clean up stale carts first
            abandonStaleCartsForUser(userId);

            // Double-check for existing cart after cleanup
            Optional<CartModel> existingCart = cartPersistencePort.findByUserIdAndStatus(userId, ACTIVE_STATUS);
            if (existingCart.isPresent()) {
                CartModel cart = existingCart.get();
                if (!isCartStale(cart)) {
                    logger.info("Found existing active cart for user {} during creation", userId);
                    return cart;
                }
            }

            // Create and save new cart
            CartModel newCart = new CartModel(userId);
            CartModel savedCart = cartPersistencePort.save(newCart);
            logger.info("Created new cart with ID {} for immediate use by user {}", savedCart.getId(), userId);

            return savedCart;

        } catch (Exception e) {
            logger.error("Error creating cart for immediate use for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to create cart for immediate use", e);
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartModel updateItemQuantity(String userId, Long articleId, int quantity) {
        validateUserId(userId);
        validateQuantity(quantity);
        logger.info("Updating item quantity for user: {} and article: {} to quantity: {}", userId, articleId, quantity);

        return executeWithRetry(
                () -> updateItemQuantityWithRetry(userId, articleId, quantity),
                "update item quantity",
                userId
        );
    }

    private CartModel updateItemQuantityWithRetry(String userId, Long articleId, int quantity) {
        CartModel cart = getActiveCartWithSessionValidation(userId);
        validateCartForOperation(cart, userId);

        cart.updateItemQuantity(articleId, quantity);

        CartModel updatedCart = cartPersistencePort.save(cart);
        logger.info("Successfully updated item quantity for user {}", userId);
        return updatedCart;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartModel removeItem(String userId, Long articleId) {
        validateUserId(userId);
        validateArticleId(articleId);
        logger.info("Removing item from cart for user: {} and article: {}", userId, articleId);

        return executeWithRetry(
                () -> removeItemWithRetry(userId, articleId),
                "remove item from cart",
                userId
        );
    }

    private CartModel removeItemWithRetry(String userId, Long articleId) {
        CartModel cart = getActiveCartWithSessionValidation(userId);
        validateCartForOperation(cart, userId);

        // Check if item exists before attempting removal
        boolean itemExists = cart.getItems().stream()
                .anyMatch(item -> item.getArticleId().equals(articleId));

        if (!itemExists) {
            logger.warn("Attempt to remove non-existent item {} from cart for user {}", articleId, userId);
            throw new CartItemNotFoundException("Article not found in cart");
        }

        cart.removeItem(articleId);

        CartModel updatedCart = cartPersistencePort.save(cart);
        logger.info("Successfully removed item from cart for user {}", userId);
        return updatedCart;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void clearCart(String userId) {
        validateUserId(userId);
        logger.info("Clearing cart for user: {}", userId);

        try {
            executeWithRetry(
                    () -> {
                        CartModel cart = getActiveCartWithSessionValidation(userId);
                        cart.clear();
                        cartPersistencePort.save(cart);
                        return null;
                    },
                    "clear cart",
                    userId
            );
            logger.info("Successfully cleared cart for user {}", userId);
        } catch (CartNotFoundException e) {
            logger.info("No active cart found for user {}, nothing to clear", userId);
        } catch (Exception e) {
            logger.error("Error clearing cart for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to clear cart", e);
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void abandonCart(String userId) {
        validateUserId(userId);
        logger.info("Abandoning cart for user: {}", userId);

        try {
            executeWithRetry(
                    () -> {
                        CartModel cart = getActiveCartWithSessionValidation(userId);
                        cart.abandon();
                        cartPersistencePort.save(cart);
                        return null;
                    },
                    "abandon cart",
                    userId
            );
            logger.info("Successfully abandoned cart for user {}", userId);
        } catch (CartNotFoundException e) {
            logger.info("No active cart found for user {}, nothing to abandon", userId);
        } catch (Exception e) {
            logger.error("Error abandoning cart for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to abandon cart", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CartModel getCartByUserAndStatus(String userId, String status) {
        validateUserId(userId);
        validateStatus(status);
        logger.debug("Retrieving cart for user: {} with status: {}", userId, status);

        return cartPersistencePort.findByUserIdAndStatus(userId, status)
                .orElseThrow(() -> new CartNotFoundException(
                        String.format("No cart found for user with status: %s", status)));
    }

    /**
     * Get active cart with enhanced session validation
     */
    private CartModel getActiveCartWithSessionValidation(String userId) {
        Optional<CartModel> cartOptional = cartPersistencePort.findByUserIdAndStatus(userId, ACTIVE_STATUS);

        if (cartOptional.isEmpty()) {
            throw new CartNotFoundException("No active cart found for user");
        }

        CartModel cart = cartOptional.get();

        // Enhanced session validation
        if (isCartStale(cart)) {
            logger.warn("Cart for user {} is stale (last updated: {}), abandoning it",
                    userId, cart.getLastUpdated());
            try {
                cart.abandon();
                cartPersistencePort.save(cart);
            } catch (Exception e) {
                logger.error("Failed to abandon stale cart during validation: {}", e.getMessage());
            }
            throw new CartNotFoundException("Cart session has expired, please create a new cart");
        }

        return cart;
    }

    /**
     * Validate cart state before performing operations
     */
    private void validateCartForOperation(CartModel cart, String userId) {
        if (cart == null) {
            throw new CartNotFoundException("Cart not found");
        }

        if (!ACTIVE_STATUS.equals(cart.getStatus())) {
            logger.warn("Attempt to operate on non-active cart for user {}, status: {}", userId, cart.getStatus());
            throw new InvalidCartOperationException("Cart is not active");
        }

        if (isCartStale(cart)) {
            logger.warn("Attempt to operate on stale cart for user {}", userId);
            throw new InvalidCartOperationException("Cart session has expired");
        }
    }

    /**
     * Check if a cart is stale (older than expiry time)
     */
    private boolean isCartStale(CartModel cart) {
        if (cart.getLastUpdated() == null) {
            return true;
        }

        LocalDateTime expiryTime = cart.getLastUpdated().plusHours(CART_EXPIRY_HOURS);
        return LocalDateTime.now().isAfter(expiryTime);
    }

    /**
     * Check if a cart is approaching expiry
     */
    private boolean isCartApproachingExpiry(CartModel cart) {
        if (cart.getLastUpdated() == null) {
            return true;
        }

        LocalDateTime warningTime = cart.getLastUpdated().plusHours(CART_EXPIRY_HOURS - CART_WARNING_HOURS);
        return LocalDateTime.now().isAfter(warningTime);
    }

    /**
     * Abandon stale carts for a user
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abandonStaleCartsForUser(String userId) {
        try {
            Optional<CartModel> activeCartOpt = cartPersistencePort.findByUserIdAndStatus(userId, ACTIVE_STATUS);

            if (activeCartOpt.isPresent()) {
                CartModel activeCart = activeCartOpt.get();

                if (isCartStale(activeCart)) {
                    logger.info("Abandoning stale cart for user {}", userId);
                    activeCart.abandon();
                    cartPersistencePort.save(activeCart);
                }
            }
        } catch (Exception e) {
            logger.warn("Error abandoning stale carts for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Cleanup expired carts (can be called by scheduled task)
     */
    @Transactional
    public int cleanupExpiredCarts() {
        logger.info("Starting cleanup of expired carts");

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(CART_EXPIRY_HOURS);

            // This would require additional repository method
            // For now, we'll log the intent
            logger.info("Would cleanup carts older than {}", cutoffTime);

            // TODO: Implement batch cleanup in repository
            return 0;
        } catch (Exception e) {
            logger.error("Error during cart cleanup: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Validation methods
     */
    private void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new InvalidParameterException("User ID cannot be empty");
        }
    }

    private void validateCartItem(CartItemModel item) {
        if (item == null) {
            throw new InvalidParameterException("Cart item cannot be null");
        }
        if (item.getArticleId() == null) {
            throw new InvalidParameterException("Article ID cannot be null");
        }
        if (item.getQuantity() <= 0) {
            throw new InvalidParameterException("Quantity must be greater than zero");
        }
        if (item.getPrice() < 0) {
            throw new InvalidParameterException("Price cannot be negative");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new InvalidParameterException("Quantity must be greater than zero");
        }
    }

    private void validateArticleId(Long articleId) {
        if (articleId == null) {
            throw new InvalidParameterException("Article ID cannot be null");
        }
    }

    private void validateStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new InvalidParameterException("Status cannot be empty");
        }
        if (!ACTIVE_STATUS.equals(status) && !ABANDONED_STATUS.equals(status) && !COMPLETED_STATUS.equals(status)) {
            throw new InvalidParameterException("Invalid status: " + status);
        }
    }

    /**
     * Functional interface for operations that can throw exceptions
     */
    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}