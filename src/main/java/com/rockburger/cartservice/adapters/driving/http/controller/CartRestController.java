package com.rockburger.cartservice.adapters.driving.http.controller;

import com.rockburger.cartservice.adapters.driving.http.dto.request.AddCartItemRequest;
import com.rockburger.cartservice.adapters.driving.http.dto.request.UpdateCartItemRequest;
import com.rockburger.cartservice.adapters.driving.http.dto.response.CartResponse;
import com.rockburger.cartservice.adapters.driving.http.mapper.ICartItemRequestMapper;
import com.rockburger.cartservice.adapters.driving.http.mapper.ICartResponseMapper;
import com.rockburger.cartservice.domain.api.ICartServicePort;
import com.rockburger.cartservice.domain.model.CartItemModel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping
    @Operation(summary = "Get active cart", description = "Retrieves the active cart for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Cart retrieved successfully")
    @ApiResponse(responseCode = "404", description = "No active cart found")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<CartResponse> getActiveCart(@AuthenticationPrincipal UserDetails userDetails) {
        logger.info("Retrieving active cart for user: {}", userDetails.getUsername());
        return ResponseEntity.ok(
                cartResponseMapper.toResponse(
                        cartServicePort.getActiveCart(userDetails.getUsername())
                )
        );
    }

    @PostMapping("/items")
    @Operation(summary = "Add item to cart", description = "Adds a new item to the user's active cart")
    @ApiResponse(responseCode = "201", description = "Item added successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "409", description = "Item already exists in cart")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AddCartItemRequest request) {
        logger.info("Adding item to cart for user: {}", userDetails.getUsername());

        CartItemModel itemModel = cartItemRequestMapper.toModel(request);
        return new ResponseEntity<>(
                cartResponseMapper.toResponse(
                        cartServicePort.addItem(userDetails.getUsername(), itemModel)
                ),
                HttpStatus.CREATED
        );
    }

    @PutMapping("/items")
    @Operation(summary = "Update item quantity", description = "Updates the quantity of an item in the cart")
    @ApiResponse(responseCode = "200", description = "Item updated successfully")
    @ApiResponse(responseCode = "404", description = "Item not found in cart")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<CartResponse> updateItemQuantity(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateCartItemRequest request) {
        logger.info("Updating item quantity for user: {}", userDetails.getUsername());

        return ResponseEntity.ok(
                cartResponseMapper.toResponse(
                        cartServicePort.updateItemQuantity(
                                userDetails.getUsername(),
                                request.getArticleId(),
                                request.getQuantity()
                        )
                )
        );
    }

    @DeleteMapping("/items/{articleId}")
    @Operation(summary = "Remove item", description = "Removes an item from the cart")
    @ApiResponse(responseCode = "200", description = "Item removed successfully")
    @ApiResponse(responseCode = "404", description = "Item not found in cart")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<CartResponse> removeItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long articleId) {
        logger.info("Removing item from cart for user: {}", userDetails.getUsername());

        return ResponseEntity.ok(
                cartResponseMapper.toResponse(
                        cartServicePort.removeItem(userDetails.getUsername(), articleId)
                )
        );
    }

    @DeleteMapping
    @Operation(summary = "Clear cart", description = "Removes all items from the cart")
    @ApiResponse(responseCode = "204", description = "Cart cleared successfully")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal UserDetails userDetails) {
        logger.info("Clearing cart for user: {}", userDetails.getUsername());
        cartServicePort.clearCart(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/abandon")
    @Operation(summary = "Abandon cart", description = "Marks the cart as abandoned")
    @ApiResponse(responseCode = "204", description = "Cart abandoned successfully")
    @PreAuthorize("hasAnyRole('client', 'auxiliar')")
    public ResponseEntity<Void> abandonCart(@AuthenticationPrincipal UserDetails userDetails) {
        logger.info("Abandoning cart for user: {}", userDetails.getUsername());
        cartServicePort.abandonCart(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}