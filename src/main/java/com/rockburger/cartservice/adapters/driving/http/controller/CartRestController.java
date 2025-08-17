package com.rockburger.cartservice.adapters.driving.http.controller;

import com.rockburger.cartservice.adapters.driving.http.dto.request.AddCartItemRequest;
import com.rockburger.cartservice.adapters.driving.http.dto.request.UpdateCartItemRequest;
import com.rockburger.cartservice.adapters.driving.http.dto.response.CartResponse;
import com.rockburger.cartservice.adapters.driving.http.mapper.ICartResponseMapper;
import com.rockburger.cartservice.domain.api.ICartServicePort;
import com.rockburger.cartservice.domain.exception.*;
import com.rockburger.cartservice.domain.model.CartItemModel;
import com.rockburger.cartservice.domain.model.CartModel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/cart")
@Tag(name = "Cart Management", description = "Shopping cart operations")
public class CartRestController {
    private static final Logger logger = LoggerFactory.getLogger(CartRestController.class);

    private final ICartServicePort cartServicePort;
    private final ICartResponseMapper cartResponseMapper;

    public CartRestController(ICartServicePort cartServicePort,
                              ICartResponseMapper cartResponseMapper) {
        this.cartServicePort = cartServicePort;
        this.cartResponseMapper = cartResponseMapper;
    }

    /**
     * Extract user ID from the current security context with enhanced validation
     */
    private String getCurrentUserId(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = null;

        logger.debug("Security context authentication: {}",
                auth != null ? auth.getClass().getSimpleName() : "null");

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            logger.debug("Authentication principal type: {}, value: {}",
                    auth.getPrincipal() != null ? auth.getPrincipal().getClass().getSimpleName() : "null",
                    auth.getPrincipal());

            // Handle different principal types
            if (auth.getPrincipal() instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) auth.getPrincipal();
                userId = userDetails.getUsername();
                logger.debug("User ID from UserDetails: {}", userId);
            } else if (auth.getPrincipal() instanceof String) {
                userId = (String) auth.getPrincipal();
                logger.debug("User ID from String principal: {}", userId);
            } else if (auth.getName() != null && !auth.getName().equals("anonymousUser")) {
                userId = auth.getName();
                logger.debug("User ID from auth name: {}", userId);
            }
        } else {
            logger.warn("No valid authentication found in security context");
        }

        logger.debug("Final resolved user ID: {}", userId);

        if (userId == null || userId.trim().isEmpty() || "anonymousUser".equals(userId)) {
            logger.error("Could not determine user ID from request or security context");
            return null;
        }

        return userId.trim();
    }

    /**
     * Validate user authentication and return appropriate error response if needed
     */
    private ResponseEntity<Object> validateAuthentication(HttpServletRequest request) {
        String userId = getCurrentUserId(request);

        if (userId == null) {
            logger.error("User not authenticated properly - returning 401 Unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Authentication required", "AUTHENTICATION_REQUIRED"));
        }

        return null; // Validation passed
    }

    @GetMapping
    @Operation(summary = "Get active cart", description = "Retrieves the active cart for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Cart retrieved successfully")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    @ApiResponse(responseCode = "404", description = "No active cart found")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<?> getActiveCart(HttpServletRequest request) {
        // Validate authentication first
        ResponseEntity<Object> authValidation = validateAuthentication(request);
        if (authValidation != null) {
            return authValidation;
        }

        String userId = getCurrentUserId(request);
        logger.info("Retrieving active cart for user: {}", userId);

        try {
            CartModel cart = cartServicePort.getActiveCart(userId);
            CartResponse response = cartResponseMapper.toResponse(cart);
            logger.debug("Successfully retrieved cart with {} items for user {}",
                    response.getItems() != null ? response.getItems().size() : 0, userId);
            return ResponseEntity.ok(response);
        } catch (CartNotFoundException e) {
            logger.info("No active cart found for user {}: {}", userId, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error retrieving cart for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve cart", "CART_RETRIEVAL_ERROR"));
        }
    }

    @PostMapping("/items")
    @Operation(summary = "Add item to cart", description = "Adds a new item to the cart")
    @ApiResponse(responseCode = "200", description = "Item added successfully")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    @ApiResponse(responseCode = "409", description = "Item already exists in cart")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<?> addItem(
            HttpServletRequest request,
            @Valid @RequestBody AddCartItemRequest itemRequest) {

        // Validate authentication first
        ResponseEntity<Object> authValidation = validateAuthentication(request);
        if (authValidation != null) {
            return authValidation;
        }

        String userId = getCurrentUserId(request);
        logger.info("Adding item to cart for user: {}, article: {}", userId, itemRequest.getArticleId());

        try {
            CartItemModel item = new CartItemModel(
                    itemRequest.getArticleId(),
                    itemRequest.getArticleName(),
                    itemRequest.getQuantity(),
                    itemRequest.getPrice()
            );

            CartModel updatedCart = cartServicePort.addItem(userId, item);
            CartResponse response = cartResponseMapper.toResponse(updatedCart);
            logger.info("Successfully added item to cart for user: {}", userId);
            return ResponseEntity.ok(response);
        } catch (DuplicateArticleException e) {
            logger.warn("Attempt to add duplicate item for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(e.getMessage(), "DUPLICATE_ARTICLE"));
        } catch (InvalidParameterException e) {
            logger.warn("Invalid parameter for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(e.getMessage(), "INVALID_PARAMETER"));
        } catch (Exception e) {
            logger.error("Error adding item to cart for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to add item to cart", "CART_ADD_ERROR"));
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
            @Valid @RequestBody UpdateCartItemRequest updateRequest) {

        // Validate authentication first
        ResponseEntity<Object> authValidation = validateAuthentication(request);
        if (authValidation != null) {
            return authValidation;
        }

        String userId = getCurrentUserId(request);
        logger.info("Updating item quantity for user: {}, article: {}, quantity: {}",
                userId, updateRequest.getArticleId(), updateRequest.getQuantity());

        try {
            CartModel updatedCart = cartServicePort.updateItemQuantity(
                    userId, updateRequest.getArticleId(), updateRequest.getQuantity());
            CartResponse response = cartResponseMapper.toResponse(updatedCart);
            logger.info("Successfully updated item quantity for user: {}", userId);
            return ResponseEntity.ok(response);
        } catch (CartNotFoundException e) {
            logger.warn("No active cart found for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("No active cart found", "CART_NOT_FOUND"));
        } catch (CartItemNotFoundException e) {
            logger.warn("Item not found in cart for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage(), "CART_ITEM_NOT_FOUND"));
        } catch (InvalidParameterException e) {
            logger.warn("Invalid parameter for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(e.getMessage(), "INVALID_PARAMETER"));
        } catch (Exception e) {
            logger.error("Error updating item quantity for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update item quantity", "CART_UPDATE_ERROR"));
        }
    }

    @DeleteMapping("/items/{articleId}")
    @Operation(summary = "Remove item", description = "Removes an item from the cart")
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
            CartModel updatedCart = cartServicePort.removeItem(userId, articleId);
            CartResponse response = cartResponseMapper.toResponse(updatedCart);
            logger.info("Successfully removed item from cart for user: {}", userId);
            return ResponseEntity.ok(response);
        } catch (CartNotFoundException e) {
            logger.warn("No active cart found for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("No active cart found", "CART_NOT_FOUND"));
        } catch (CartItemNotFoundException e) {
            logger.warn("Item not found in cart for user {}, article {}: {}", userId, articleId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage(), "CART_ITEM_NOT_FOUND"));
        } catch (InvalidCartOperationException e) {
            logger.warn("Invalid cart operation for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage(), "INVALID_CART_OPERATION"));
        } catch (Exception e) {
            logger.error("Error removing item from cart for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to remove item from cart", "CART_REMOVE_ERROR"));
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
        } catch (CartNotFoundException e) {
            logger.info("No active cart found for user {}, nothing to clear: {}", userId, e.getMessage());
            return ResponseEntity.noContent().build(); // Still return success
        } catch (Exception e) {
            logger.error("Error clearing cart for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to clear cart", "CART_CLEAR_ERROR"));
        }
    }

    @PostMapping("/abandon")
    @Operation(summary = "Abandon cart", description = "Marks the current cart as abandoned")
    @ApiResponse(responseCode = "204", description = "Cart abandoned successfully")
    @ApiResponse(responseCode = "401", description = "User not authenticated")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<?> abandonCart(HttpServletRequest request) {
        // Validate authentication first
        ResponseEntity<Object> authValidation = validateAuthentication(request);
        if (authValidation != null) {
            return authValidation;
        }

        String userId = getCurrentUserId(request);
        logger.info("Abandoning cart for user: {}", userId);

        try {
            cartServicePort.abandonCart(userId);
            logger.info("Successfully abandoned cart for user: {}", userId);
            return ResponseEntity.noContent().build();
        } catch (CartNotFoundException e) {
            logger.info("No active cart found for user {}, nothing to abandon: {}", userId, e.getMessage());
            return ResponseEntity.noContent().build(); // Still return success
        } catch (Exception e) {
            logger.error("Error abandoning cart for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to abandon cart", "CART_ABANDON_ERROR"));
        }
    }

    /**
     * Error response DTO for consistent error handling
     */
    public static class ErrorResponse {
        private String message;
        private String errorCode;
        private long timestamp;

        public ErrorResponse(String message, String errorCode) {
            this.message = message;
            this.errorCode = errorCode;
            this.timestamp = System.currentTimeMillis();
        }

        public String getMessage() { return message; }
        public String getErrorCode() { return errorCode; }
        public long getTimestamp() { return timestamp; }
    }
}