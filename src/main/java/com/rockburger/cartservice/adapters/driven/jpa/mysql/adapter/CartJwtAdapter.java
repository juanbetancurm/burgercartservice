package com.rockburger.cartservice.adapters.driven.jpa.mysql.adapter;

import com.rockburger.cartservice.configuration.security.JwtCartKeyProvider;
import com.rockburger.cartservice.domain.exception.CartTokensException;
import com.rockburger.cartservice.domain.model.CartUserModel;
import com.rockburger.cartservice.domain.spi.ICartJwtPersistencePort;
import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CartJwtAdapter implements ICartJwtPersistencePort {
    private static final Logger logger = LoggerFactory.getLogger(CartJwtAdapter.class);

    private final JwtCartKeyProvider jwtCartKeyProvider;
    private final String jwtSecret;

    public CartJwtAdapter(
            JwtCartKeyProvider jwtCartKeyProvider,
            String jwtCartSecretKey) {
        this.jwtCartKeyProvider = jwtCartKeyProvider;
        this.jwtSecret = jwtCartSecretKey;
    }

    @Override
    public CartUserModel validateToken(String token) {
        Claims claims = getClaimsFromToken(token);

        // Extract user details from claims
        Long userId = null;
        try {
            // Handle the case where userId might be an Integer or Long
            Object userIdObj = claims.get("userId");
            if (userIdObj instanceof Integer) {
                userId = ((Integer) userIdObj).longValue();
            } else if (userIdObj instanceof Long) {
                userId = (Long) userIdObj;
            }
        } catch (Exception e) {
            logger.warn("Error extracting userId from token: {}", e.getMessage());
        }

        String role = claims.get("role", String.class);

        // Normalize role format between services
        // The main app might use "client" while cart service expects "ROLE_client"
        if (role != null && !role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }

        return new CartUserModel(
                userId,
                claims.getSubject(), // email/username
                role
        );
    }

    @Override
    public String getUserEmailFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    @Override
    public String getUserRoleFromToken(String token) {
        String role = getClaimsFromToken(token).get("role", String.class);

        // Normalize role format
        if (role != null && !role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }

        return role;
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(jwtCartKeyProvider.getSigningKey(jwtSecret))
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("JWT token expired: {}", e.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            logger.error("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(jwtCartKeyProvider.getSigningKey(jwtSecret))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            logger.error("Token has expired", e);
            throw new CartTokensException("Token has expired");
        } catch (JwtException e) {
            logger.error("Invalid token", e);
            throw new CartTokensException("Invalid token: " + e.getMessage());
        }
    }
}