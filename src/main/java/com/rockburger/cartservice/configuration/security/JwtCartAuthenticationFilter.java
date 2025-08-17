package com.rockburger.cartservice.configuration.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockburger.cartservice.domain.model.CartUserModel;
import com.rockburger.cartservice.domain.spi.ICartJwtPersistencePort;
import com.rockburger.cartservice.domain.exception.CartTokensException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(1)
public class JwtCartAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtCartAuthenticationFilter.class);

    private final ICartJwtPersistencePort cartJwtPersistencePort;
    private final ObjectMapper objectMapper;

    // Paths that don't require authentication
    private static final String[] PUBLIC_PATHS = {
            "/swagger-ui/",
            "/v3/api-docs/",
            "/swagger-resources/",
            "/webjars/",
            "/error",
            "/actuator/health"
    };

    public JwtCartAuthenticationFilter(ICartJwtPersistencePort cartJwtPersistencePort) {
        this.cartJwtPersistencePort = cartJwtPersistencePort;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        logger.debug("Processing {} request to '{}'", method, requestURI);

        // Skip authentication for public paths
        if (isPublicPath(requestURI)) {
            logger.debug("Skipping authentication for public path: {}", requestURI);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = extractJwtFromRequest(request);
            logger.debug("Processing cart service request to '{}' with JWT: {}", requestURI,
                    jwt != null ? "present" : "not present");

            if (jwt != null) {
                TokenValidationResult validationResult = validateToken(jwt);

                if (validationResult.isValid()) {
                    CartUserModel user = validationResult.getUser();

                    // Normalize role format for Spring Security
                    String role = user.getRole();
                    if (role != null && !role.startsWith("ROLE_")) {
                        role = "ROLE_" + role;
                    }

                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority(role)
                    );

                    // Store user details including email as principal
                    String email = user.getEmail();
                    logger.debug("Authenticated user email: {} with role: {}", email, role);

                    // Store JWT token as credentials and email as principal
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    email, // Use email as principal
                                    jwt,   // Store token as credentials
                                    authorities
                            );

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // Store userId in request attribute for easy access by controllers
                    request.setAttribute("userId", email);
                    request.setAttribute("userRole", user.getRole());

                    logger.debug("User authenticated in cart service with role: {}", role);
                } else {
                    // Handle invalid token
                    handleInvalidToken(request, response, validationResult);
                    return;
                }
            } else {
                // No token provided for protected resource
                logger.warn("No JWT token provided for protected cart service resource: {}", requestURI);
                handleMissingToken(response, requestURI);
                return;
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication in cart service for {}: {}", requestURI, e.getMessage(), e);
            handleAuthenticationError(response, "Authentication processing failed", HttpStatus.INTERNAL_SERVER_ERROR);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Validate JWT token and return validation result
     */
    private TokenValidationResult validateToken(String jwt) {
        try {
            // First check if token is valid (not expired, properly signed)
            if (!cartJwtPersistencePort.isTokenValid(jwt)) {
                logger.warn("Token validation failed - token is invalid or expired");
                return new TokenValidationResult(false, null, "Invalid or expired token");
            }

            // If valid, extract user information
            CartUserModel user = cartJwtPersistencePort.validateToken(jwt);
            return new TokenValidationResult(true, user, null);

        } catch (CartTokensException e) {
            logger.warn("Cart token validation failed: {}", e.getMessage());

            // Determine if token is expired or just invalid
            String errorMessage = e.getMessage().toLowerCase().contains("expired") ?
                    "Token has expired" : "Invalid token";
            return new TokenValidationResult(false, null, errorMessage);

        } catch (Exception e) {
            logger.error("Unexpected error during token validation: {}", e.getMessage(), e);
            return new TokenValidationResult(false, null, "Token validation failed");
        }
    }

    /**
     * Handle cases where no token is provided
     */
    private void handleMissingToken(HttpServletResponse response, String requestURI) throws IOException {
        logger.debug("No authentication token provided for: {}", requestURI);
        handleAuthenticationError(response, "Authentication token required", HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handle invalid token scenarios
     */
    private void handleInvalidToken(HttpServletRequest request, HttpServletResponse response,
                                    TokenValidationResult validationResult) throws IOException {
        String requestURI = request.getRequestURI();
        String errorMessage = validationResult.getErrorMessage();

        logger.warn("Invalid token for cart service request to {}: {}", requestURI, errorMessage);

        // Determine appropriate HTTP status
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        if (errorMessage.contains("expired")) {
            status = HttpStatus.UNAUTHORIZED; // 401 for expired tokens
        } else if (errorMessage.contains("invalid")) {
            status = HttpStatus.UNAUTHORIZED; // 401 for invalid tokens
        }

        handleAuthenticationError(response, errorMessage, status);
    }

    /**
     * Send JSON error response for authentication failures
     */
    private void handleAuthenticationError(HttpServletResponse response, String message,
                                           HttpStatus status) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        errorResponse.put("status", status.value());
        errorResponse.put("error", status.getReasonPhrase());
        errorResponse.put("message", message);
        errorResponse.put("path", "cart-authentication");
        errorResponse.put("service", "cart-service");

        // Add specific error codes for client handling
        if (message.contains("expired")) {
            errorResponse.put("errorCode", "TOKEN_EXPIRED");
        } else if (message.contains("invalid")) {
            errorResponse.put("errorCode", "INVALID_TOKEN");
        } else if (message.contains("required")) {
            errorResponse.put("errorCode", "TOKEN_REQUIRED");
        } else {
            errorResponse.put("errorCode", "AUTHENTICATION_FAILED");
        }

        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);

        logger.debug("Sent cart service authentication error response: {}", jsonResponse);
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Check if the request path is public (doesn't require authentication)
     */
    private boolean isPublicPath(String requestURI) {
        for (String publicPath : PUBLIC_PATHS) {
            if (requestURI.startsWith(publicPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inner class to hold token validation results
     */
    private static class TokenValidationResult {
        private final boolean valid;
        private final CartUserModel user;
        private final String errorMessage;

        public TokenValidationResult(boolean valid, CartUserModel user, String errorMessage) {
            this.valid = valid;
            this.user = user;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public CartUserModel getUser() {
            return user;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}