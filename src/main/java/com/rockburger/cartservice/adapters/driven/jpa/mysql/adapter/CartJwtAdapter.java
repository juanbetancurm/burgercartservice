package com.rockburger.cartservice.adapters.driven.jpa.mysql.adapter;

import com.rockburger.cartservice.configuration.security.JwtCartKeyProvider;
import com.rockburger.cartservice.domain.exception.CartTokensException;
import com.rockburger.cartservice.domain.model.CartUserModel;
import com.rockburger.cartservice.domain.spi.ICartJwtPersistencePort;
import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CartJwtAdapter implements ICartJwtPersistencePort {
    private static final Logger logger = LoggerFactory.getLogger(CartJwtAdapter.class);

    private final JwtCartKeyProvider jwtCartKeyProvider;
    private final String jwtSecret;

    public CartJwtAdapter(
            JwtCartKeyProvider jwtCartKeyProvider,
            @Value("${jwt.secret}") String jwtSecret) {
        this.jwtCartKeyProvider = jwtCartKeyProvider;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public CartUserModel validateToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return new CartUserModel(
                null, // ID is not needed for cart operations
                claims.getSubject(),
                claims.get("role", String.class)
        );
    }

    @Override
    public String getUserEmailFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    @Override
    public String getUserRoleFromToken(String token) {
        return getClaimsFromToken(token).get("role", String.class);
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(jwtCartKeyProvider.getSigningKey(jwtSecret))
                    .build()
                    .parseClaimsJws(token);
            return true;
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
            throw new CartTokensException("Token has expired");
        } catch (JwtException e) {
            throw new CartTokensException("Invalid token");
        }
    }
}