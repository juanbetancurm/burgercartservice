package com.rockburger.cartservice.adapters.driven.jpa.mysql.adapter;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartEntity;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper.ICartEntityMapper;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.repository.ICartRepository;
import com.rockburger.cartservice.domain.exception.CartNotFoundException;
import com.rockburger.cartservice.domain.exception.ConcurrentCartModificationException;
import com.rockburger.cartservice.domain.model.CartModel;
import com.rockburger.cartservice.domain.spi.ICartPersistencePort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.OptimisticLockException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CartAdapter implements ICartPersistencePort {
    private static final Logger logger = LoggerFactory.getLogger(CartAdapter.class);

    private final ICartRepository cartRepository;
    private final ICartEntityMapper cartEntityMapper;

    // Session management constants
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String ABANDONED_STATUS = "ABANDONED";
    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final int CART_EXPIRY_HOURS = 24;
    private static final int MAX_OPTIMISTIC_LOCK_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 100;

    public CartAdapter(ICartRepository cartRepository, ICartEntityMapper cartEntityMapper) {
        this.cartRepository = cartRepository;
        this.cartEntityMapper = cartEntityMapper;
    }

    @Override
    @Transactional
    public CartModel save(CartModel cartModel) {
        logger.debug("Saving cart for user: {}", cartModel.getUserId());

        try {
            // Validate cart before saving
            validateCartForSave(cartModel);

            return saveWithOptimisticLockRetry(cartModel);

        } catch (ConcurrentCartModificationException e) {
            // Re-throw concurrency exceptions as-is
            throw e;
        } catch (Exception e) {
            logger.error("Error saving cart for user {}: {}", cartModel.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Failed to save cart", e);
        }
    }

    /**
     * Save cart with automatic retry on optimistic locking failures
     */
    private CartModel saveWithOptimisticLockRetry(CartModel cartModel) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_LOCK_RETRIES; attempt++) {
            try {
                logger.debug("Attempting to save cart for user {} (attempt {})", cartModel.getUserId(), attempt);

                CartEntity cartEntity = cartEntityMapper.toEntity(cartModel);

                // Special handling for optimistic locking
                CartEntity savedEntity;
                try {
                    savedEntity = cartRepository.save(cartEntity);
                    logger.debug("Successfully saved cart with ID: {} on attempt {}", savedEntity.getId(), attempt);
                } catch (ObjectOptimisticLockingFailureException e) {
                    handleOptimisticLockingFailure(e, cartModel.getUserId(), attempt);
                    if (attempt == MAX_OPTIMISTIC_LOCK_RETRIES) {
                        throw new ConcurrentCartModificationException(
                                "Cart was modified by another session. Please refresh and try again.");
                    }
                    waitAndRetry(attempt);
                    cartModel = refreshCartModelForRetry(cartModel);
                    continue;
                } catch (OptimisticLockException e) {
                    handleOptimisticLockingFailure(e, cartModel.getUserId(), attempt);
                    if (attempt == MAX_OPTIMISTIC_LOCK_RETRIES) {
                        throw new ConcurrentCartModificationException(
                                "Cart was modified by another session. Please refresh and try again.");
                    }
                    waitAndRetry(attempt);
                    cartModel = refreshCartModelForRetry(cartModel);
                    continue;
                } catch (OptimisticLockingFailureException e) {
                    handleOptimisticLockingFailure(e, cartModel.getUserId(), attempt);
                    if (attempt == MAX_OPTIMISTIC_LOCK_RETRIES) {
                        throw new ConcurrentCartModificationException(
                                "Cart was modified by another session. Please refresh and try again.");
                    }
                    waitAndRetry(attempt);
                    cartModel = refreshCartModelForRetry(cartModel);
                    continue;
                }

                CartModel savedModel = cartEntityMapper.toModel(savedEntity);

                // Log cart summary for debugging
                logger.debug("Saved cart summary: User: {}, Items: {}, Status: {}",
                        savedModel.getUserId(),
                        savedModel.getItems() != null ? savedModel.getItems().size() : 0,
                        savedModel.getStatus());

                return savedModel;

            } catch (ObjectOptimisticLockingFailureException e) {
                lastException = e;
                logger.warn("Optimistic locking failure on attempt {} for user {}: {}",
                        attempt, cartModel.getUserId(), e.getMessage());
                if (attempt == MAX_OPTIMISTIC_LOCK_RETRIES) {
                    break;
                }
            } catch (OptimisticLockException e) {
                lastException = e;
                logger.warn("Optimistic lock exception on attempt {} for user {}: {}",
                        attempt, cartModel.getUserId(), e.getMessage());
                if (attempt == MAX_OPTIMISTIC_LOCK_RETRIES) {
                    break;
                }
            } catch (OptimisticLockingFailureException e) {
                lastException = e;
                logger.warn("Spring optimistic locking failure on attempt {} for user {}: {}",
                        attempt, cartModel.getUserId(), e.getMessage());
                if (attempt == MAX_OPTIMISTIC_LOCK_RETRIES) {
                    break;
                }
            } catch (Exception e) {
                logger.error("Unexpected error on attempt {} for user {}: {}",
                        attempt, cartModel.getUserId(), e.getMessage(), e);
                throw e;
            }
        }

        // If we get here, all retry attempts failed
        logger.error("Failed to save cart after {} attempts for user {}",
                MAX_OPTIMISTIC_LOCK_RETRIES, cartModel.getUserId());
        throw new ConcurrentCartModificationException(
                "Unable to save cart due to concurrent modifications. Please refresh and try again.");
    }

    /**
     * Wait before retry with exponential backoff
     */
    private void waitAndRetry(int attempt) {
        try {
            Thread.sleep(RETRY_DELAY_MS * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Save operation interrupted", ie);
        }
    }

    /**
     * Handle optimistic locking failure with detailed logging
     */
    private void handleOptimisticLockingFailure(Exception e, String userId, int attempt) {
        String errorDetails = extractOptimisticLockErrorDetails(e);
        logger.warn("Optimistic locking failure for cart user {} on attempt {}: {} - Details: {}",
                userId, attempt, e.getMessage(), errorDetails);
    }

    /**
     * Extract meaningful details from optimistic locking exceptions
     */
    private String extractOptimisticLockErrorDetails(Exception e) {
        StringBuilder details = new StringBuilder();

        if (e instanceof ObjectOptimisticLockingFailureException) {
            ObjectOptimisticLockingFailureException ex = (ObjectOptimisticLockingFailureException) e;
            details.append("Object: ").append(ex.getPersistentClassName());
            if (ex.getIdentifier() != null) {
                details.append(", ID: ").append(ex.getIdentifier());
            }
        } else if (e instanceof OptimisticLockException) {
            OptimisticLockException ex = (OptimisticLockException) e;
            if (ex.getEntity() != null) {
                details.append("Entity: ").append(ex.getEntity().getClass().getSimpleName());
            }
        } else if (e instanceof OptimisticLockingFailureException) {
            details.append("Spring OptimisticLockingFailureException");
        }

        return details.toString();
    }

    /**
     * Refresh cart model for retry attempt
     */
    private CartModel refreshCartModelForRetry(CartModel cartModel) {
        try {
            // Try to fetch the latest version of the cart from database
            Optional<CartModel> latestCart = findByUserIdAndStatus(cartModel.getUserId(), cartModel.getStatus());

            if (latestCart.isPresent()) {
                logger.debug("Refreshed cart model for retry - found existing cart with ID: {}",
                        latestCart.get().getId());
                return latestCart.get();
            } else {
                logger.debug("No existing cart found during refresh, will create new one");
                // Return the original model but reset ID to null for fresh creation
                cartModel.setId(null);
                return cartModel;
            }
        } catch (Exception e) {
            logger.warn("Failed to refresh cart model for retry, using original: {}", e.getMessage());
            return cartModel;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CartModel> findByUserIdAndStatus(String userId, String status) {
        logger.debug("Finding cart for user: {} with status: {}", userId, status);

        try {
            validateUserId(userId);
            validateStatus(status);

            List<CartEntity> cartEntities = cartRepository.findByUserIdAndStatus(userId, status);

            if (cartEntities.isEmpty()) {
                logger.debug("No cart found for user {} with status {}", userId, status);
                return Optional.empty();
            }

            // Handle multiple active carts (cleanup scenario)
            if (cartEntities.size() > 1) {
                logger.warn("Found {} carts for user {} with status {}. Cleaning up duplicates.",
                        cartEntities.size(), userId, status);
                return handleMultipleCarts(cartEntities, userId, status);
            }

            CartEntity cartEntity = cartEntities.get(0);
            CartModel cartModel = cartEntityMapper.toModel(cartEntity);

            // Fixed: Check if cart entity is expired (using entity's lastUpdated field)
            if (isCartEntityExpired(cartEntity) && ACTIVE_STATUS.equals(status)) {
                logger.info("Found expired active cart for user {}, abandoning it", userId);
                abandonExpiredCart(cartEntity);
                return Optional.empty();
            }

            logger.debug("Found cart: User: {}, Items: {}, Status: {}",
                    cartModel.getUserId(),
                    cartModel.getItems() != null ? cartModel.getItems().size() : 0,
                    cartModel.getStatus());
            return Optional.of(cartModel);

        } catch (Exception e) {
            logger.error("Error finding cart for user {} with status {}: {}", userId, status, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Fixed: Check if cart entity is expired using entity's timestamp
     */
    private boolean isCartEntityExpired(CartEntity cartEntity) {
        if (cartEntity.getLastUpdated() == null) {
            return true; // Consider null timestamps as expired
        }

        LocalDateTime expiryThreshold = LocalDateTime.now().minusHours(CART_EXPIRY_HOURS);
        return cartEntity.getLastUpdated().isBefore(expiryThreshold);
    }

    @Override
    @Transactional
    public void deleteByUserId(String userId) {
        logger.debug("Deleting carts for user: {}", userId);

        try {
            validateUserId(userId);

            // Find all carts for the user
            List<CartEntity> userCarts = cartRepository.findByUserId(userId);

            if (userCarts.isEmpty()) {
                logger.info("No carts found to delete for user: {}", userId);
                return;
            }

            // Log what we're about to delete
            userCarts.forEach(cart ->
                    logger.debug("Deleting cart ID {} with status {} for user {}",
                            cart.getId(), cart.getStatus(), userId));

            // Delete all carts for the user with retry logic for optimistic locking
            deleteCartsWithRetry(userCarts, userId);

            logger.info("Successfully deleted {} cart(s) for user: {}", userCarts.size(), userId);

        } catch (Exception e) {
            logger.error("Error deleting carts for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete carts for user", e);
        }
    }

    /**
     * Delete carts with retry logic for optimistic locking failures
     */
    private void deleteCartsWithRetry(List<CartEntity> userCarts, String userId) {
        for (CartEntity cart : userCarts) {
            boolean deleted = false;
            Exception lastException = null;

            for (int attempt = 1; attempt <= MAX_OPTIMISTIC_LOCK_RETRIES && !deleted; attempt++) {
                try {
                    cartRepository.delete(cart);
                    deleted = true;
                    logger.debug("Successfully deleted cart ID {} on attempt {}", cart.getId(), attempt);
                } catch (ObjectOptimisticLockingFailureException e) {
                    lastException = e;
                    logger.warn("Optimistic locking failure deleting cart ID {} on attempt {}: {}",
                            cart.getId(), attempt, e.getMessage());
                    handleDeleteRetry(cart, attempt);
                } catch (OptimisticLockException e) {
                    lastException = e;
                    logger.warn("Optimistic lock exception deleting cart ID {} on attempt {}: {}",
                            cart.getId(), attempt, e.getMessage());
                    handleDeleteRetry(cart, attempt);
                } catch (OptimisticLockingFailureException e) {
                    lastException = e;
                    logger.warn("Spring optimistic locking failure deleting cart ID {} on attempt {}: {}",
                            cart.getId(), attempt, e.getMessage());
                    handleDeleteRetry(cart, attempt);
                } catch (Exception e) {
                    logger.error("Unexpected error deleting cart ID {}: {}", cart.getId(), e.getMessage());
                    throw e;
                }
            }

            if (!deleted) {
                logger.error("Failed to delete cart ID {} after {} attempts due to optimistic locking",
                        cart.getId(), MAX_OPTIMISTIC_LOCK_RETRIES);
                // Continue with other carts rather than failing the entire operation
            }
        }
    }

    /**
     * Handle retry logic for delete operations
     */
    private void handleDeleteRetry(CartEntity cart, int attempt) {
        if (attempt < MAX_OPTIMISTIC_LOCK_RETRIES) {
            try {
                Thread.sleep(RETRY_DELAY_MS * attempt);
                // Refresh the entity for retry
                Optional<CartEntity> refreshedCart = cartRepository.findById(cart.getId());
                if (refreshedCart.isPresent()) {
                    cart = refreshedCart.get();
                } else {
                    // Cart was already deleted by another transaction
                    logger.info("Cart ID {} was already deleted by another transaction", cart.getId());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Delete operation interrupted", ie);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUserIdAndStatus(String userId, String status) {
        logger.debug("Checking if cart exists for user: {} with status: {}", userId, status);

        try {
            validateUserId(userId);
            validateStatus(status);

            boolean exists = cartRepository.existsByUserIdAndStatus(userId, status);
            logger.debug("Cart exists for user {} with status {}: {}", userId, status, exists);

            return exists;
        } catch (Exception e) {
            logger.error("Error checking cart existence for user {} with status {}: {}",
                    userId, status, e.getMessage(), e);
            return false;
        }
    }

    @Override
    @Transactional
    public void updateCartStatus(String userId, String oldStatus, String newStatus) {
        logger.debug("Updating cart status for user: {} from {} to {}", userId, oldStatus, newStatus);

        try {
            validateUserId(userId);
            validateStatus(oldStatus);
            validateStatus(newStatus);

            List<CartEntity> carts = cartRepository.findByUserIdAndStatus(userId, oldStatus);

            if (carts.isEmpty()) {
                logger.info("No carts found with status {} for user {}", oldStatus, userId);
                return;
            }

            int updatedCount = 0;
            for (CartEntity cart : carts) {
                if (updateCartStatusWithRetry(cart, newStatus)) {
                    updatedCount++;
                }
            }

            logger.info("Successfully updated {} cart(s) status from {} to {} for user {}",
                    updatedCount, oldStatus, newStatus, userId);

        } catch (Exception e) {
            logger.error("Error updating cart status for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to update cart status", e);
        }
    }

    /**
     * Update cart status with retry logic for optimistic locking
     */
    private boolean updateCartStatusWithRetry(CartEntity cart, String newStatus) {
        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_LOCK_RETRIES; attempt++) {
            try {
                cart.setStatus(newStatus);
                cart.setLastUpdated(LocalDateTime.now());
                cartRepository.save(cart);
                logger.debug("Updated cart ID {} status to {} on attempt {}", cart.getId(), newStatus, attempt);
                return true;
            } catch (ObjectOptimisticLockingFailureException e) {
                handleStatusUpdateRetry(cart, attempt, e);
            } catch (OptimisticLockException e) {
                handleStatusUpdateRetry(cart, attempt, e);
            } catch (OptimisticLockingFailureException e) {
                handleStatusUpdateRetry(cart, attempt, e);
            } catch (Exception e) {
                logger.error("Unexpected error updating cart ID {} status: {}", cart.getId(), e.getMessage());
                return false;
            }
        }

        logger.error("Failed to update cart ID {} status after {} attempts",
                cart.getId(), MAX_OPTIMISTIC_LOCK_RETRIES);
        return false;
    }

    /**
     * Handle retry logic for status update operations
     */
    private void handleStatusUpdateRetry(CartEntity cart, int attempt, Exception e) {
        logger.warn("Optimistic locking failure updating cart ID {} status on attempt {}: {}",
                cart.getId(), attempt, e.getMessage());

        if (attempt < MAX_OPTIMISTIC_LOCK_RETRIES) {
            try {
                Thread.sleep(RETRY_DELAY_MS * attempt);
                // Refresh the entity
                Optional<CartEntity> refreshedCart = cartRepository.findById(cart.getId());
                if (refreshedCart.isPresent()) {
                    cart = refreshedCart.get();
                } else {
                    logger.warn("Cart ID {} no longer exists", cart.getId());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Update operation interrupted", ie);
            }
        }
    }

    /**
     * Clean up expired carts for all users
     */
    @Transactional
    public int cleanupExpiredCarts() {
        logger.info("Starting cleanup of expired carts");

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(CART_EXPIRY_HOURS);

            // Find active carts that are older than the cutoff time
            List<CartEntity> expiredCarts = cartRepository.findActiveCartsOlderThan(cutoffTime);

            if (expiredCarts.isEmpty()) {
                logger.info("No expired carts found for cleanup");
                return 0;
            }

            logger.info("Found {} expired carts to clean up", expiredCarts.size());

            int cleanedCount = 0;
            for (CartEntity cart : expiredCarts) {
                if (updateCartStatusWithRetry(cart, ABANDONED_STATUS)) {
                    cleanedCount++;
                    logger.debug("Abandoned expired cart ID {} for user {}", cart.getId(), cart.getUserId());
                }
            }

            logger.info("Successfully cleaned up {} expired carts", cleanedCount);
            return cleanedCount;

        } catch (Exception e) {
            logger.error("Error during expired cart cleanup: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Handle multiple carts scenario (should not happen but defensive programming)
     */
    private Optional<CartModel> handleMultipleCarts(List<CartEntity> cartEntities, String userId, String status) {
        // Sort by last updated, keep the most recent
        List<CartEntity> sortedCarts = cartEntities.stream()
                .sorted((c1, c2) -> c2.getLastUpdated().compareTo(c1.getLastUpdated()))
                .collect(Collectors.toList());

        CartEntity latestCart = sortedCarts.get(0);

        // Mark older carts as abandoned with retry logic
        for (int i = 1; i < sortedCarts.size(); i++) {
            CartEntity oldCart = sortedCarts.get(i);
            if (updateCartStatusWithRetry(oldCart, ABANDONED_STATUS)) {
                logger.info("Abandoned duplicate cart ID {} for user {}", oldCart.getId(), userId);
            }
        }

        return Optional.of(cartEntityMapper.toModel(latestCart));
    }

    /**
     * Abandon an expired cart with retry logic
     */
    private void abandonExpiredCart(CartEntity cartEntity) {
        if (updateCartStatusWithRetry(cartEntity, ABANDONED_STATUS)) {
            logger.info("Abandoned expired cart ID {} for user {}", cartEntity.getId(), cartEntity.getUserId());
        } else {
            logger.error("Failed to abandon expired cart ID {} for user {}",
                    cartEntity.getId(), cartEntity.getUserId());
        }
    }

    /**
     * Validate cart before saving
     */
    private void validateCartForSave(CartModel cartModel) {
        if (cartModel == null) {
            throw new IllegalArgumentException("Cart model cannot be null");
        }

        if (cartModel.getUserId() == null || cartModel.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("Cart model must have a valid user ID");
        }

        validateUserId(cartModel.getUserId());
        validateStatus(cartModel.getStatus());
    }

    /**
     * Validate user ID
     */
    private void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
    }

    /**
     * Validate status
     */
    private void validateStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }

        if (!ACTIVE_STATUS.equals(status) && !ABANDONED_STATUS.equals(status) && !COMPLETED_STATUS.equals(status)) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
    }
}