package com.rockburger.cartservice.domain.spi;

import com.rockburger.cartservice.domain.model.CartModel;
import java.util.Optional;

public interface ICartPersistencePort {
    // Basic CRUD operations
    CartModel save(CartModel cartModel);
    Optional<CartModel> findByUserIdAndStatus(String userId, String status);
    void deleteByUserId(String userId);

    // Cart status operations
    boolean existsByUserIdAndStatus(String userId, String status);
    void updateCartStatus(String userId, String oldStatus, String newStatus);

    // Maintenance operations
    void deleteExpiredCarts(int expirationHours);
}