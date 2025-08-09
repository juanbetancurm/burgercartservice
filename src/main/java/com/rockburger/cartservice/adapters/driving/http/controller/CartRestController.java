package com.rockburger.cartservice.adapters.driving.http.controller;

import com.rockburger.cartservice.adapters.driving.http.dto.request.AddCartItemRequest;
import com.rockburger.cartservice.adapters.driving.http.dto.request.UpdateCartItemRequest;
import com.rockburger.cartservice.adapters.driving.http.dto.response.CartResponse;
import com.rockburger.cartservice.adapters.driving.http.mapper.ICartItemRequestMapper;
import com.rockburger.cartservice.adapters.driving.http.mapper.ICartResponseMapper;
import com.rockburger.cartservice.domain.api.ICartServicePort;
import com.rockburger.cartservice.domain.exception.CartNotFoundException;
import com.rockburger.cartservice.domain.exception.DuplicateArticleException;
import com.rockburger.cartservice.domain.model.CartItemModel;
import com.rockburger.cartservice.domain.model.CartModel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/cart")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Cart", description = "Cart management endpoints")
public class CartRestController {
    private static final Logger logger = LoggerFactory.getLogger(CartRestController.class);

    private final ICartServicePort cartServicePort;
    private final ICartItemRequestMapper cartItemRequestMapper;
    private final ICartResponseMapper cartResponseMapper;

    public CartRestController(
            ICartServicePort cartServicePort,
            ICartItemRequestMapper cartItemRequestMapper,
            ICartResponseMapper cartResponseMapper) {
        this.cartServicePort = cartServicePort;
        this.cartItemRequestMapper = cartItemRequestMapper;
        this.cartResponseMapper = cartResponseMapper;
    }

    private String getCurrentUserId(HttpServletRequest request) {
        // First try to get from request attribute (set by filter)
        String userId = (String) request.getAttribute("userId");

        // If not found, try to get from security context
        if (userId == null || userId.isEmpty()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                userId = auth.getPrincipal().toString();
            }
        }

        logger.debug("Current user ID: {}", userId);

        if (userId == null || userId.isEmpty()) {
            logger.error("Could not determine user ID from request or security context");
            throw new IllegalStateException("User not authenticated");
        }

        return userId;
    }

    @GetMapping
    @Operation(summary = "Get active cart", description = "Retrieves the active cart for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Cart retrieved successfully")
    @ApiResponse(responseCode = "404", description = "No active cart found")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<CartResponse> getActiveCart(HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        logger.info("Retrieving active cart for user: {}", userId);

        try {
            CartModel cart = cartServicePort.getActiveCart(userId);
            CartResponse response = cartResponseMapper.toResponse(cart);
            logger.debug("Successfully retrieved cart with {} items for user {}",
                    response.getItems() != null ? response.getItems().size() : 0, userId);
            return ResponseEntity.ok(response);
        } catch (CartNotFoundException e) {
            logger.info("No active cart found for user: {}, creating new cart", userId);
            CartModel newCart = cartServicePort.createCart(userId);
            return ResponseEntity.ok(cartResponseMapper.toResponse(newCart));
        } catch (Exception e) {
            logger.error("Error retrieving active cart for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/items")
    @Operation(summary = "Add item to cart", description = "Adds a new item to the user's active cart")
    @ApiResponse(responseCode = "201", description = "Item added successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "409", description = "Item already exists in cart")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<CartResponse> addItem(
            HttpServletRequest request,
            @Valid @RequestBody AddCartItemRequest itemRequest) {
        String userId = getCurrentUserId(request);
        logger.info("Adding item to cart for user: {}, item: {}", userId, itemRequest);

        try {
            CartItemModel itemModel = cartItemRequestMapper.toModel(itemRequest);
            CartModel updatedCart = cartServicePort.addItem(userId, itemModel);
            CartResponse response = cartResponseMapper.toResponse(updatedCart);

            logger.info("Successfully added item to cart for user {}. Cart now has {} items",
                    userId, response.getItems() != null ? response.getItems().size() : 0);

            return new ResponseEntity<>(response, HttpStatus.CREATED);

        } catch (DuplicateArticleException e) {
            logger.warn("Duplicate item addition attempt for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            logger.error("Error adding item to cart for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/items")
    @Operation(summary = "Update item quantity", description = "Updates the quantity of an item in the cart")
    @ApiResponse(responseCode = "200", description = "Item updated successfully")
    @ApiResponse(responseCode = "404", description = "Item not found in cart")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<CartResponse> updateItemQuantity(
            HttpServletRequest request,
            @Valid @RequestBody UpdateCartItemRequest updateRequest) {
        String userId = getCurrentUserId(request);
        logger.info("Updating item quantity for user: {}, article: {}", userId, updateRequest.getArticleId());

        try {
            CartModel updatedCart = cartServicePort.updateItemQuantity(
                    userId,
                    updateRequest.getArticleId(),
                    updateRequest.getQuantity()
            );
            return ResponseEntity.ok(cartResponseMapper.toResponse(updatedCart));
        } catch (Exception e) {
            logger.error("Error updating item quantity for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/items/{articleId}")
    @Operation(summary = "Remove item", description = "Removes an item from the cart")
    @ApiResponse(responseCode = "200", description = "Item removed successfully")
    @ApiResponse(responseCode = "404", description = "Item not found in cart")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<CartResponse> removeItem(
            HttpServletRequest request,
            @PathVariable Long articleId) {
        String userId = getCurrentUserId(request);
        logger.info("Removing item from cart for user: {}, article: {}", userId, articleId);

        try {
            CartModel updatedCart = cartServicePort.removeItem(userId, articleId);
            return ResponseEntity.ok(cartResponseMapper.toResponse(updatedCart));
        } catch (Exception e) {
            logger.error("Error removing item from cart for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping
    @Operation(summary = "Clear cart", description = "Removes all items from the cart")
    @ApiResponse(responseCode = "204", description = "Cart cleared successfully")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<Void> clearCart(HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        logger.info("Clearing cart for user: {}", userId);

        try {
            cartServicePort.clearCart(userId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error clearing cart for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/abandon")
    @Operation(summary = "Abandon cart", description = "Marks the cart as abandoned")
    @ApiResponse(responseCode = "204", description = "Cart abandoned successfully")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<Void> abandonCart(HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        logger.info("Abandoning cart for user: {}", userId);

        try {
            cartServicePort.abandonCart(userId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error abandoning cart for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}