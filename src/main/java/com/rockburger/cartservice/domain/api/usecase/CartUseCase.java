package com.rockburger.cartservice.domain.api.usecase;

import com.rockburger.cartservice.domain.api.ICartServicePort;
import com.rockburger.cartservice.domain.exception.*;
import com.rockburger.cartservice.domain.model.CartModel;
import com.rockburger.cartservice.domain.model.CartItemModel;
import com.rockburger.cartservice.domain.spi.ICartPersistencePort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CartUseCase implements ICartServicePort {
    private static final Logger logger = LoggerFactory.getLogger(CartUseCase.class);
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String ABANDONED_STATUS = "ABANDONED";

    private final ICartPersistencePort cartPersistencePort;

    public CartUseCase(ICartPersistencePort cartPersistencePort) {
        this.cartPersistencePort = cartPersistencePort;
    }

    @Override
    public CartModel createCart(String userId) {
        logger.info("Creating new cart for user: {}", userId);

        // Check if user already has an active cart
        if (cartPersistencePort.existsByUserIdAndStatus(userId, ACTIVE_STATUS)) {
            logger.warn("User {} already has an active cart", userId);
            throw new InvalidCartOperationException("User already has an active cart");
        }

        // Create and save new cart
        CartModel newCart = new CartModel(userId);
        return cartPersistencePort.save(newCart);
    }

    @Override
    public CartModel getActiveCart(String userId) {
        logger.debug("Retrieving active cart for user: {}", userId);
        return cartPersistencePort.findByUserIdAndStatus(userId, ACTIVE_STATUS)
                .orElseThrow(() -> new CartNotFoundException("No active cart found for user"));
    }

    @Override
    public CartModel addItem(String userId, CartItemModel item) {
        logger.info("Adding item to cart for user: {}", userId);

        CartModel cart = getActiveCart(userId);
        cart.addItem(item);

        return cartPersistencePort.save(cart);
    }

    @Override
    public CartModel updateItemQuantity(String userId, Long articleId, int quantity) {
        logger.info("Updating item quantity for user: {} and article: {}", userId, articleId);

        CartModel cart = getActiveCart(userId);
        cart.updateItemQuantity(articleId, quantity);

        return cartPersistencePort.save(cart);
    }

    @Override
    public CartModel removeItem(String userId, Long articleId) {
        logger.info("Removing item from cart for user: {} and article: {}", userId, articleId);

        CartModel cart = getActiveCart(userId);
        cart.removeItem(articleId);

        return cartPersistencePort.save(cart);
    }

    @Override
    public void clearCart(String userId) {
        logger.info("Clearing cart for user: {}", userId);

        CartModel cart = getActiveCart(userId);
        cart.clear();

        cartPersistencePort.save(cart);
    }

    @Override
    public void abandonCart(String userId) {
        logger.info("Abandoning cart for user: {}", userId);

        CartModel cart = getActiveCart(userId);
        cart.abandon();

        cartPersistencePort.save(cart);
    }

    @Override
    public CartModel getCartByUserAndStatus(String userId, String status) {
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