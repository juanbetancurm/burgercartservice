package com.rockburger.cartservice.configuration;

public final class ConstantsCart {
    private ConstantsCart() {
        throw new IllegalStateException("Utility class");
    }

    // Cart status constants
    public static final String CART_STATUS_ACTIVE = "ACTIVE";
    public static final String CART_STATUS_ABANDONED = "ABANDONED";
    public static final String CART_STATUS_COMPLETED = "COMPLETED";

    // Error messages
    public static final String ERROR_CART_NOT_FOUND = "Cart not found";
    public static final String ERROR_ITEM_NOT_FOUND = "Item not found in cart";
    public static final String ERROR_DUPLICATE_ITEM = "Item already exists in cart";
    public static final String ERROR_INVALID_QUANTITY = "Invalid item quantity";
    public static final String ERROR_CART_NOT_ACTIVE = "Cart is not active";
    public static final String ERROR_CONCURRENT_MODIFICATION = "Cart was modified concurrently";

    // Security messages
    public static final String ERROR_INSUFFICIENT_PERMISSIONS = "Insufficient permissions";
    public static final String ERROR_INVALID_TOKEN = "Invalid or expired token";

    // Validation messages
    public static final String VALIDATION_USERID_REQUIRED = "User ID is required";
    public static final String VALIDATION_ARTICLEID_REQUIRED = "Article ID is required";
    public static final String VALIDATION_QUANTITY_POSITIVE = "Quantity must be greater than zero";
    public static final String VALIDATION_PRICE_POSITIVE = "Price must be greater than or equal to zero";
}