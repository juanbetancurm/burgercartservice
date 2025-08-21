package com.rockburger.cartservice.adapters.driving.http.controller;

import com.rockburger.cartservice.adapters.driving.http.dto.request.AddCartItemRequest;
import com.rockburger.cartservice.adapters.driving.http.dto.request.UpdateCartItemRequest;
import com.rockburger.cartservice.adapters.driving.http.dto.response.CartResponse;
import com.rockburger.cartservice.adapters.driving.http.mapper.ICartResponseMapper;
import com.rockburger.cartservice.domain.api.ICartServicePort;
import com.rockburger.cartservice.domain.model.CartModel;
import com.rockburger.cartservice.domain.model.CartItemModel;
import com.rockburger.cartservice.domain.exception.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/cart")
@Tag(name = "Cart Management", description = "Cart operations API")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class CartRestController {

    private static final Logger logger = LoggerFactory.getLogger(CartRestController.class);
    private static final String SERVICE_NAME = "cart-service";

    @Autowired
    private ICartServicePort cartServicePort;

    @Autowired
    private ICartResponseMapper cartResponseMapper;

    /**
     * Enhanced authentication validation
     */
    private ResponseEntity<Object> validateAuthentication(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.warn("Missing or invalid Authorization header");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(createErrorResponse(HttpStatus.UNAUTHORIZED,
                                "Missing or invalid authorization token",
                                "UNAUTHORIZED", request.getRequestURI()));
            }
            return null; // Authentication valid
        } catch (Exception e) {
            logger.error("Error validating authentication: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse(HttpStatus.UNAUTHORIZED,
                            "Authentication validation failed",
                            "AUTH_ERROR", request.getRequestURI()));
        }
    }

    /**
     * Enhanced request data validation
     */
    private ResponseEntity<Object> validateRequestData(BindingResult bindingResult, HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            List<String> errors = bindingResult.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.toList());

            logger.warn("Request validation failed: {}", errors);
            return ResponseEntity.badRequest()
                    .body(createErrorResponse(HttpStatus.BAD_REQUEST,
                            "Invalid request data: " + String.join(", ", errors),
                            "VALIDATION_ERROR", request.getRequestURI()));
        }
        return null; // Validation passed
    }

    /**
     * Extract user ID from request with better error handling
     */
    private String getCurrentUserId(HttpServletRequest request) {
        try {
            String userId = (String) request.getAttribute("userId");
            if (userId == null || userId.trim().isEmpty()) {
                throw new IllegalStateException("User ID not found in request attributes");
            }
            return userId;
        } catch (Exception e) {
            logger.error("Failed to extract user ID from request: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to extract user information from request", e);
        }
    }

    /**
     * Create standardized error response
     */
    private ErrorResponse createErrorResponse(HttpStatus status, String message, String errorCode, String path) {
        return new ErrorResponse(
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                status.value(),
                status.getReasonPhrase(),
                message,
                errorCode,
                path,
                SERVICE_NAME
        );
    }

    /**
     * Centralized cart operation exception handler
     */
    private ResponseEntity<?> handleCartOperationException(String userId, Exception e, String operation, HttpServletRequest request) {
        logger.error("Cart operation '{}' failed for user {}: {}", operation, userId, e.getMessage(), e);

        // Handle optimistic locking and concurrency issues
        if (e.getCause() instanceof org.springframework.orm.ObjectOptimisticLockingFailureException) {
            return handleConcurrencyException(userId, e, operation, request);
        }

        // Handle specific business exceptions
        if (e instanceof CartNotFoundException) {
            logger.info("Cart not found for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse(HttpStatus.NOT_FOUND,
                            "No active cart found", "CART_NOT_FOUND", request.getRequestURI()));
        }

        if (e instanceof CartItemNotFoundException) {
            logger.warn("Cart item not found for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse(HttpStatus.NOT_FOUND,
                            e.getMessage(), "CART_ITEM_NOT_FOUND", request.getRequestURI()));
        }

        if (e instanceof DuplicateArticleException) {
            logger.warn("Duplicate article for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(createErrorResponse(HttpStatus.CONFLICT,
                            e.getMessage(), "DUPLICATE_ARTICLE", request.getRequestURI()));
        }

        if (e instanceof InvalidParameterException) {
            logger.warn("Invalid parameter for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(createErrorResponse(HttpStatus.BAD_REQUEST,
                            e.getMessage(), "INVALID_PARAMETER", request.getRequestURI()));
        }

        if (e instanceof InvalidCartOperationException) {
            logger.warn("Invalid cart operation for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createErrorResponse(HttpStatus.BAD_REQUEST,
                            e.getMessage(), "INVALID_CART_OPERATION", request.getRequestURI()));
        }

        // Handle generic runtime exceptions
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred during " + operation,
                        "INTERNAL_ERROR", request.getRequestURI()));
    }

    /**
     * Handle concurrency exceptions with user-friendly messages
     */
    private ResponseEntity<?> handleConcurrencyException(String userId, Exception e, String operation, HttpServletRequest request) {
        logger.warn("Concurrency conflict for user {} during {}: {}", userId, operation, e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(createErrorResponse(HttpStatus.CONFLICT,
                        "Cart was modified by another session. Please refresh and try again.",
                        "CONCURRENCY_CONFLICT", request.getRequestURI()));
    }

    @GetMapping
    @Operation(summary = "Get active cart", description = "Retrieves the current active cart for the user")
    @ApiResponse(responseCode = "200", description = "Cart retrieved successfully")
    @ApiResponse(responseCode = "404", description = "No active cart found")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<?> getActiveCart(HttpServletRequest request) {
        // Validate authentication first
        ResponseEntity<Object> authValidation = validateAuthentication(request);
        if (authValidation != null) {
            return authValidation;
        }

        String userId = getCurrentUserId(request);
        logger.info("Getting active cart for user: {}", userId);

        try {
            CartModel cart = cartServicePort.getActiveCart(userId);
            CartResponse response = cartResponseMapper.toResponse(cart);
            logger.info("Successfully retrieved cart for user: {}, items count: {}",
                    userId, response.getItems() != null ? response.getItems().size() : 0);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return handleCartOperationException(userId, e, "retrieve cart", request);
        }
    }

    @PostMapping("/items")
    @Operation(summary = "Add item to cart", description = "Adds a new item to the cart")
    @ApiResponse(responseCode = "200", description = "Item added successfully")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    @ApiResponse(responseCode = "409", description = "Item already exists in cart or cart state conflict")
    @ApiResponse(responseCode = "500", description = "Server error")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<?> addItem(
            HttpServletRequest request,
            @Valid @RequestBody AddCartItemRequest itemRequest,
            BindingResult bindingResult) {

        // Validate authentication first
        ResponseEntity<Object> authValidation = validateAuthentication(request);
        if (authValidation != null) {
            return authValidation;
        }

        // Validate request data
        ResponseEntity<Object> dataValidation = validateRequestData(bindingResult, request);
        if (dataValidation != null) {
            return dataValidation;
        }

        String userId = getCurrentUserId(request);
        logger.info("Adding item to cart for user: {}, article: {}, quantity: {}",
                userId, itemRequest.getArticleId(), itemRequest.getQuantity());

        try {
            // Additional validation for business rules
            if (itemRequest.getQuantity() <= 0) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse(HttpStatus.BAD_REQUEST,
                                "Quantity must be greater than 0",
                                "INVALID_QUANTITY", request.getRequestURI()));
            }

            // Fixed: Use proper Double comparison (no Double.ZERO constant exists in Java)
            if (itemRequest.getPrice() == null || itemRequest.getPrice() <= 0.0) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse(HttpStatus.BAD_REQUEST,
                                "Price must be greater than 0",
                                "INVALID_PRICE", request.getRequestURI()));
            }

            CartItemModel item = new CartItemModel(
                    itemRequest.getArticleId(),
                    itemRequest.getArticleName(),
                    itemRequest.getQuantity(),
                    itemRequest.getPrice()
            );

            CartModel updatedCart = cartServicePort.addItem(userId, item);
            CartResponse response = cartResponseMapper.toResponse(updatedCart);

            logger.info("Successfully added item to cart for user: {}, new cart size: {}",
                    userId, response.getItems() != null ? response.getItems().size() : 0);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return handleCartOperationException(userId, e, "add item", request);
        }
    }

    @PutMapping("/items")
    @Operation(summary = "Update item quantity", description = "Updates the quantity of an item in the cart")
    @ApiResponse(responseCode = "200", description = "Item quantity updated successfully")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    @ApiResponse(responseCode = "404", description = "Item not found in cart")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<?> updateItemQuantity(
            HttpServletRequest request,
            @Valid @RequestBody UpdateCartItemRequest updateRequest,
            BindingResult bindingResult) {

        // Validate authentication first
        ResponseEntity<Object> authValidation = validateAuthentication(request);
        if (authValidation != null) {
            return authValidation;
        }

        // Validate request data
        ResponseEntity<Object> dataValidation = validateRequestData(bindingResult, request);
        if (dataValidation != null) {
            return dataValidation;
        }

        String userId = getCurrentUserId(request);
        logger.info("Updating item quantity for user: {}, article: {}, quantity: {}",
                userId, updateRequest.getArticleId(), updateRequest.getQuantity());

        try {
            // Additional validation
            if (updateRequest.getQuantity() <= 0) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse(HttpStatus.BAD_REQUEST,
                                "Quantity must be greater than 0",
                                "INVALID_QUANTITY", request.getRequestURI()));
            }

            CartModel updatedCart = cartServicePort.updateItemQuantity(
                    userId, updateRequest.getArticleId(), updateRequest.getQuantity());
            CartResponse response = cartResponseMapper.toResponse(updatedCart);

            logger.info("Successfully updated item quantity for user: {}", userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return handleCartOperationException(userId, e, "update item quantity", request);
        }
    }

    @DeleteMapping("/items/{articleId}")
    @Operation(summary = "Remove item from cart", description = "Removes an item from the cart")
    @ApiResponse(responseCode = "200", description = "Item removed successfully")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    @ApiResponse(responseCode = "404", description = "Item not found in cart")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<?> removeItem(
            HttpServletRequest request,
            @PathVariable Long articleId) {

        // Validate authentication first
        ResponseEntity<Object> authValidation = validateAuthentication(request);
        if (authValidation != null) {
            return authValidation;
        }

        String userId = getCurrentUserId(request);
        logger.info("Removing item from cart for user: {}, article: {}", userId, articleId);

        try {
            // Additional validation
            if (articleId == null || articleId <= 0) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse(HttpStatus.BAD_REQUEST,
                                "Invalid article ID",
                                "INVALID_ARTICLE_ID", request.getRequestURI()));
            }

            CartModel updatedCart = cartServicePort.removeItem(userId, articleId);
            CartResponse response = cartResponseMapper.toResponse(updatedCart);

            logger.info("Successfully removed item from cart for user: {}", userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return handleCartOperationException(userId, e, "remove item", request);
        }
    }

    @DeleteMapping
    @Operation(summary = "Clear cart", description = "Removes all items from the cart")
    @ApiResponse(responseCode = "204", description = "Cart cleared successfully")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<?> clearCart(HttpServletRequest request) {
        // Validate authentication first
        ResponseEntity<Object> authValidation = validateAuthentication(request);
        if (authValidation != null) {
            return authValidation;
        }

        String userId = getCurrentUserId(request);
        logger.info("Clearing cart for user: {}", userId);

        try {
            cartServicePort.clearCart(userId);
            logger.info("Successfully cleared cart for user: {}", userId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return handleCartOperationException(userId, e, "clear cart", request);
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Service health check endpoint")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Cart service is healthy");
    }

    /**
     * Standardized error response structure for this controller
     */
    public static class ErrorResponse {
        private String timestamp;
        private int status;
        private String error;
        private String message;
        private String errorCode;
        private String path;
        private String service;

        public ErrorResponse(String timestamp, int status, String error, String message,
                             String errorCode, String path, String service) {
            this.timestamp = timestamp;
            this.status = status;
            this.error = error;
            this.message = message;
            this.errorCode = errorCode;
            this.path = path;
            this.service = service;
        }

        // Getters
        public String getTimestamp() { return timestamp; }
        public int getStatus() { return status; }
        public String getError() { return error; }
        public String getMessage() { return message; }
        public String getErrorCode() { return errorCode; }
        public String getPath() { return path; }
        public String getService() { return service; }

        // Setters
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public void setStatus(int status) { this.status = status; }
        public void setError(String error) { this.error = error; }
        public void setMessage(String message) { this.message = message; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        public void setPath(String path) { this.path = path; }
        public void setService(String service) { this.service = service; }
    }
}