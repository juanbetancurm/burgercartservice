package com.rockburger.cartservice.configuration.security;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class JwtCartKeyProvider {

    private final String jwtSecret;

    public JwtCartKeyProvider(String jwtCartSecretKey) {
        this.jwtSecret = jwtCartSecretKey;
    }

    public SecretKey getSigningKey(String secret) {
        // Use the configured secret if no specific secret provided
        if (secret == null || secret.isEmpty()) {
            secret = jwtSecret;
        }

        byte[] keyBytes = Base64.getDecoder().decode(secret.getBytes(StandardCharsets.UTF_8));
        return Keys.hmacShaKeyFor(keyBytes);
    }
}