package com.rockburger.cartservice.domain.model;

import com.rockburger.cartservice.domain.exception.CartItemNotFoundException;
import com.rockburger.cartservice.domain.exception.DuplicateArticleException;
import com.rockburger.cartservice.domain.exception.InvalidCartOperationException;
import com.rockburger.cartservice.domain.exception.InvalidParameterException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CartModel {
    private Long id;
    private String userId;
    private List<CartItemModel> items;
    private double total;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private String status;
    private String sessionId;
    private Integer version; // For optimistic locking
    private Boolean expiryWarningSent; // NEW FIELD from migration

    // Status constants
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String ABANDONED_STATUS = "ABANDONED";
    private static final String COMPLETED_STATUS = "COMPLETED";

    // Session management constants
    private static final int MAX_CART_AGE_HOURS = 24;
    private static final int SESSION_WARNING_HOURS = 4;
    private static final int MAX_ITEMS_PER_CART = 50;

    // Default constructor for frameworks
    public CartModel() {
        this.items = new ArrayList<>();
        this.total = 0.0;
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
        this.status = ACTIVE_STATUS;
        this.sessionId = generateSessionId();
        this.version = 0;
        this.expiryWarningSent = false;
    }

    public CartModel(String userId) {
        this();
        validateUserId(userId);
        this.userId = userId;
    }

    /**
     * Generate unique session ID for cart
     */
    private String generateSessionId() {
        return "cart_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Validate user ID
     */
    private void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new InvalidParameterException("User ID cannot be empty");
        }
    }

    /**
     * Validate cart status for operations
     */
    private void validateCartStatus() {
        if (!ACTIVE_STATUS.equals(status)) {
            throw new InvalidCartOperationException("Cart is not active (current status: " + status + ")");
        }

        if (isExpired()) {
            throw new InvalidCartOperationException("Cart session has expired");
        }
    }

    /**
     * Validate cart limits
     */
    private void validateCartLimits() {
        if (items.size() >= MAX_ITEMS_PER_CART) {
            throw new InvalidCartOperationException("Cart has reached maximum item limit (" + MAX_ITEMS_PER_CART + ")");
        }
    }

    /**
     * Add item to cart with validation
     */
    public void addItem(CartItemModel newItem) {
        validateCartStatus();
        validateCartLimits();

        if (newItem == null) {
            throw new InvalidParameterException("Cart item cannot be null");
        }

        // Validate item data
        if (newItem.getArticleId() == null) {
            throw new InvalidParameterException("Article ID cannot be null");
        }

        if (newItem.getQuantity() <= 0) {
            throw new InvalidParameterException("Item quantity must be greater than zero");
        }

        if (newItem.getPrice() < 0) {
            throw new InvalidParameterException("Item price cannot be negative");
        }

        // Check for duplicate items
        boolean itemExists = items.stream()
                .anyMatch(item -> item.getArticleId().equals(newItem.getArticleId()));

        if (itemExists) {
            throw new DuplicateArticleException("Article already exists in cart (ID: " + newItem.getArticleId() + ")");
        }

        items.add(newItem);
        updateTotalAndTimestamp();
    }

    /**
     * Update item quantity with validation
     */
    public void updateItemQuantity(Long articleId, int newQuantity) {
        validateCartStatus();

        if (articleId == null) {
            throw new InvalidParameterException("Article ID cannot be null");
        }

        if (newQuantity <= 0) {
            throw new InvalidParameterException("Quantity must be greater than zero");
        }

        if (newQuantity > 999) {
            throw new InvalidParameterException("Quantity cannot exceed 999");
        }

        CartItemModel item = findItemByArticleId(articleId)
                .orElseThrow(() -> new CartItemNotFoundException("Article not found in cart (ID: " + articleId + ")"));

        item.updateQuantity(newQuantity);
        updateTotalAndTimestamp();
    }

    /**
     * Remove item from cart with validation
     */
    public void removeItem(Long articleId) {
        validateCartStatus();

        if (articleId == null) {
            throw new InvalidParameterException("Article ID cannot be null");
        }

        boolean removed = items.removeIf(item -> item.getArticleId().equals(articleId));
        if (!removed) {
            throw new CartItemNotFoundException("Article not found in cart (ID: " + articleId + ")");
        }
        updateTotalAndTimestamp();
    }

    /**
     * Clear all items from cart
     */
    public void clear() {
        validateCartStatus();
        items.clear();
        updateTotalAndTimestamp();
    }

    /**
     * Mark cart as abandoned
     */
    public void abandon() {
        this.status = ABANDONED_STATUS;
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Mark cart as completed
     */
    public void complete() {
        validateCartStatus();
        this.status = COMPLETED_STATUS;
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Refresh cart session (extend its validity)
     */
    public void refreshSession() {
        if (ACTIVE_STATUS.equals(status)) {
            this.lastUpdated = LocalDateTime.now();
            this.sessionId = generateSessionId();
        }
    }

    /**
     * Update total and timestamp
     */
    private void updateTotalAndTimestamp() {
        this.total = items.stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
        this.lastUpdated = LocalDateTime.now();
        this.version++;
    }

    /**
     * Find item by article ID
     */
    private Optional<CartItemModel> findItemByArticleId(Long articleId) {
        return items.stream()
                .filter(item -> item.getArticleId().equals(articleId))
                .findFirst();
    }

    /**
     * Check if cart has expired based on last update time
     */
    public boolean isExpired() {
        if (lastUpdated == null) {
            return true;
        }

        LocalDateTime expiryTime = lastUpdated.plusHours(MAX_CART_AGE_HOURS);
        return LocalDateTime.now().isAfter(expiryTime);
    }

    /**
     * Check if cart is approaching expiration
     */
    public boolean isApproachingExpiry() {
        if (lastUpdated == null) {
            return true;
        }

        LocalDateTime warningTime = lastUpdated.plusHours(MAX_CART_AGE_HOURS - SESSION_WARNING_HOURS);
        return LocalDateTime.now().isAfter(warningTime);
    }

    /**
     * Get remaining session time in minutes
     */
    public long getRemainingSessionMinutes() {
        if (lastUpdated == null || isExpired()) {
            return 0;
        }

        LocalDateTime expiryTime = lastUpdated.plusHours(MAX_CART_AGE_HOURS);
        LocalDateTime now = LocalDateTime.now();

        return java.time.Duration.between(now, expiryTime).toMinutes();
    }

    /**
     * Check if cart is empty
     */
    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    /**
     * Get item count
     */
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    /**
     * Get total quantity of all items
     */
    public int getTotalQuantity() {
        return items.stream()
                .mapToInt(CartItemModel::getQuantity)
                .sum();
    }

    /**
     * Validate cart integrity
     */
    public boolean isValid() {
        try {
            // Check basic fields
            if (userId == null || userId.trim().isEmpty()) {
                return false;
            }

            if (status == null || status.trim().isEmpty()) {
                return false;
            }

            if (items == null) {
                return false;
            }

            // Check if expired
            if (isExpired()) {
                return false;
            }

            // Validate each item
            for (CartItemModel item : items) {
                if (item.getArticleId() == null ||
                        item.getQuantity() <= 0 ||
                        item.getPrice() < 0) {
                    return false;
                }
            }

            // Check total consistency
            double calculatedTotal = items.stream()
                    .mapToDouble(item -> item.getPrice() * item.getQuantity())
                    .sum();

            return Math.abs(calculatedTotal - total) < 0.01; // Allow small floating point differences

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get cart summary for logging/debugging
     */
    public String getSummary() {
        return String.format("Cart[id=%d, userId=%s, status=%s, items=%d, total=%.2f, sessionId=%s, expired=%s]",
                id, userId, status, getItemCount(), total, sessionId, isExpired());
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) {
        validateUserId(userId);
        this.userId = userId;
    }

    public List<CartItemModel> getItems() {
        return items != null ? new ArrayList<>(items) : new ArrayList<>();
    }
    public void setItems(List<CartItemModel> items) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        updateTotalAndTimestamp();
    }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Boolean getExpiryWarningSent() { return expiryWarningSent; }
    public void setExpiryWarningSent(Boolean expiryWarningSent) { this.expiryWarningSent = expiryWarningSent; }

    // Status check methods
    public boolean isActive() { return ACTIVE_STATUS.equals(status); }
    public boolean isAbandoned() { return ABANDONED_STATUS.equals(status); }
    public boolean isCompleted() { return COMPLETED_STATUS.equals(status); }
}