package com.rockburger.cartservice.domain.spi;

import com.rockburger.cartservice.domain.model.CartUserModel;

// Cart-specific JWT persistence port
public interface ICartJwtPersistencePort {
    CartUserModel validateToken(String token);
    String getUserEmailFromToken(String token);
    String getUserRoleFromToken(String token);
    boolean isTokenValid(String token);
}