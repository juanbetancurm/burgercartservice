package com.rockburger.cartservice.domain.api.usecase;

import com.rockburger.cartservice.domain.api.ICartServicePort;
import com.rockburger.cartservice.domain.exception.*;
import com.rockburger.cartservice.domain.model.CartModel;
import com.rockburger.cartservice.domain.model.CartItemModel;
import com.rockburger.cartservice.domain.spi.ICartPersistencePort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

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

        // Check if user already has an active cart
        try {
            CartModel existingCart = cartPersistencePort.findByUserIdAndStatus(userId, ACTIVE_STATUS)
                    .orElse(null);

            if (existingCart != null) {
                logger.info("User {} already has an active cart with ID {}", userId, existingCart.getId());
                return existingCart;
            }
        } catch (Exception e) {
            logger.warn("Error checking for existing cart: {}", e.getMessage());
        }

        // Create and save new cart
        CartModel newCart = new CartModel(userId);
        try {
            CartModel savedCart = cartPersistencePort.save(newCart);
            logger.info("Created new cart with ID {} for user {}", savedCart.getId(), userId);
            return savedCart;
        } catch (Exception e) {
            logger.error("Error creating new cart: {}", e.getMessage(), e);
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

        // Get or create cart
        CartModel cart;
        try {
            // Try to get existing cart
            cart = getActiveCart(userId);
            logger.info("Found existing cart ID {} for user {}", cart.getId(), userId);
        } catch (CartNotFoundException e) {
            // Create new cart if not found
            logger.info("No active cart found for user {}, creating a new one", userId);
            cart = createCart(userId);
        }

        try {
            // Add item to cart
            cart.addItem(item);

            // Save and return updated cart
            CartModel updatedCart = cartPersistencePort.save(cart);
            logger.info("Item successfully added to cart for user {}. Cart now has {} items",
                    userId, updatedCart.getItems().size());

            return updatedCart;
        } catch (Exception e) {
            logger.error("Error adding item to cart: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add item to cart: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public CartModel updateItemQuantity(String userId, Long articleId, int quantity) {
        validateUserId(userId);
        logger.info("Updating item quantity for user: {} and article: {}", userId, articleId);

        CartModel cart = getActiveCart(userId);
        cart.updateItemQuantity(articleId, quantity);

        return cartPersistencePort.save(cart);
    }

    @Override
    @Transactional
    public CartModel removeItem(String userId, Long articleId) {
        validateUserId(userId);
        logger.info("Removing item from cart for user: {} and article: {}", userId, articleId);

        CartModel cart = getActiveCart(userId);
        cart.removeItem(articleId);

        return cartPersistencePort.save(cart);
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
        } catch (CartNotFoundException e) {
            logger.info("No active cart found for user {}, nothing to clear", userId);
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
        } catch (CartNotFoundException e) {
            logger.info("No active cart found for user {}, nothing to abandon", userId);
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