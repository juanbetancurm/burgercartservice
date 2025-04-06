package com.rockburger.cartservice.domain.api;

import com.rockburger.cartservice.domain.model.CartModel;
import com.rockburger.cartservice.domain.model.CartItemModel;

public interface ICartServicePort {
    // Cart management
    CartModel createCart(String userId);
    CartModel getActiveCart(String userId);
    void clearCart(String userId);
    void abandonCart(String userId);

    // Cart item operations
    CartModel addItem(String userId, CartItemModel item);
    CartModel updateItemQuantity(String userId, Long articleId, int quantity);
    CartModel removeItem(String userId, Long articleId);

    // Cart retrieval
    CartModel getCartByUserAndStatus(String userId, String status);
}