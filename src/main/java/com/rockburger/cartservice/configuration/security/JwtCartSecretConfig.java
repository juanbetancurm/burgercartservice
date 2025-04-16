package com.rockburger.cartservice.configuration.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Configuration class for JWT secret management in Cart Service.
 * Ensures consistency with the main application's JWT configuration.
 */
@Configuration
public class JwtCartSecretConfig {
    private static final Logger logger = LoggerFactory.getLogger(JwtCartSecretConfig.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final Environment environment;

    public JwtCartSecretConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public String jwtCartSecretKey() {
        // Check for environment-specific override
        String envSecret = environment.getProperty("JWT_SECRET");
        if (envSecret != null && !envSecret.isEmpty()) {
            logger.debug("Using JWT secret from environment variable");
            return envSecret;
        }

        // Fall back to configured value
        if (jwtSecret == null || jwtSecret.isEmpty() || jwtSecret.startsWith("${")) {
            logger.error("JWT secret not properly configured in cart service!");
            throw new IllegalStateException(
                    "JWT secret not properly configured! Please set jwt.secret in config or JWT_SECRET env variable."
            );
        }

        logger.debug("Using JWT secret from application configuration");
        return jwtSecret;
    }
}