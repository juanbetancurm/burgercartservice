package com.rockburger.cartservice.domain.api.usecase;

import com.rockburger.cartservice.domain.api.ICartServicePort;
import com.rockburger.cartservice.domain.exception.*;
import com.rockburger.cartservice.domain.model.CartModel;
import com.rockburger.cartservice.domain.model.CartItemModel;
import com.rockburger.cartservice.domain.spi.ICartPersistencePort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public class CartUseCase implements ICartServicePort {
    private static final Logger logger = LoggerFactory.getLogger(CartUseCase.class);
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String ABANDONED_STATUS = "ABANDONED";

    private final ICartPersistencePort cartPersistencePort;

    public CartUseCase(ICartPersistencePort cartPersistencePort) {
        this.cartPersistencePort = cartPersistencePort;
    }

    @Override
    @Transactional
    public CartModel createCart(String userId) {
        validateUserId(userId);
        logger.info("Creating new cart for user: {}", userId);

        try {
            // Check if user already has an active cart
            Optional<CartModel> existingCart = cartPersistencePort.findByUserIdAndStatus(userId, ACTIVE_STATUS);
            if (existingCart.isPresent()) {
                logger.info("User {} already has an active cart with ID {}", userId, existingCart.get().getId());
                return existingCart.get();
            }

            // Create and save new cart
            CartModel newCart = new CartModel(userId);
            CartModel savedCart = cartPersistencePort.save(newCart);
            logger.info("Created new cart with ID {} for user {}", savedCart.getId(), userId);
            return savedCart;

        } catch (Exception e) {
            logger.error("Error creating new cart for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to create new cart", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CartModel getActiveCart(String userId) {
        validateUserId(userId);
        logger.debug("Retrieving active cart for user: {}", userId);

        return cartPersistencePort.findByUserIdAndStatus(userId, ACTIVE_STATUS)
                .orElseThrow(() -> {
                    logger.warn("No active cart found for user: {}", userId);
                    return new CartNotFoundException("No active cart found for user");
                });
    }

    @Override
    @Transactional
    public CartModel addItem(String userId, CartItemModel item) {
        validateUserId(userId);

        if (item == null) {
            throw new InvalidParameterException("Cart item cannot be null");
        }

        logger.info("Adding item to cart for user: {} - Article ID: {}, Name: {}, Quantity: {}",
                userId, item.getArticleId(), item.getArticleName(), item.getQuantity());

        try {
            // Get or create cart
            CartModel cart;
            try {
                cart = getActiveCart(userId);
                logger.debug("Found existing cart ID {} for user {}", cart.getId(), userId);
            } catch (CartNotFoundException e) {
                logger.info("No active cart found for user {}, creating a new one", userId);
                cart = createCart(userId);
            }

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

        } catch (DuplicateArticleException e) {
            // Re-throw business exceptions as-is
            throw e;
        } catch (Exception e) {
            logger.error("Error adding item to cart for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to add item to cart: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public CartModel updateItemQuantity(String userId, Long articleId, int quantity) {
        validateUserId(userId);
        logger.info("Updating item quantity for user: {} and article: {} to quantity: {}", userId, articleId, quantity);

        try {
            CartModel cart = getActiveCart(userId);
            cart.updateItemQuantity(articleId, quantity);

            CartModel updatedCart = cartPersistencePort.save(cart);
            logger.info("Successfully updated item quantity for user {}", userId);
            return updatedCart;

        } catch (Exception e) {
            logger.error("Error updating item quantity for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to update item quantity", e);
        }
    }

    @Override
    @Transactional
    public CartModel removeItem(String userId, Long articleId) {
        validateUserId(userId);
        logger.info("Removing item from cart for user: {} and article: {}", userId, articleId);

        try {
            CartModel cart = getActiveCart(userId);
            cart.removeItem(articleId);

            CartModel updatedCart = cartPersistencePort.save(cart);
            logger.info("Successfully removed item from cart for user {}", userId);
            return updatedCart;

        } catch (Exception e) {
            logger.error("Error removing item from cart for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to remove item from cart", e);
        }
    }

    @Override
    @Transactional
    public void clearCart(String userId) {
        validateUserId(userId);
        logger.info("Clearing cart for user: {}", userId);

        try {
            CartModel cart = getActiveCart(userId);
            cart.clear();
            cartPersistencePort.save(cart);
            logger.info("Successfully cleared cart for user {}", userId);
        } catch (CartNotFoundException e) {
            logger.info("No active cart found for user {}, nothing to clear", userId);
        } catch (Exception e) {
            logger.error("Error clearing cart for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to clear cart", e);
        }
    }

    @Override
    @Transactional
    public void abandonCart(String userId) {
        validateUserId(userId);
        logger.info("Abandoning cart for user: {}", userId);

        try {
            CartModel cart = getActiveCart(userId);
            cart.abandon();
            cartPersistencePort.save(cart);
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
        logger.debug("Retrieving cart for user: {} with status: {}", userId, status);

        return cartPersistencePort.findByUserIdAndStatus(userId, status)
                .orElseThrow(() -> new CartNotFoundException(
                        String.format("No cart found for user with status: %s", status)));
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new InvalidParameterException("User ID cannot be empty");
        }
    }
}