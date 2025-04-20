package com.rockburger.cartservice.domain.model;

import com.rockburger.cartservice.domain.exception.CartItemNotFoundException;
import com.rockburger.cartservice.domain.exception.DuplicateArticleException;
import com.rockburger.cartservice.domain.exception.InvalidCartOperationException;
import com.rockburger.cartservice.domain.exception.InvalidParameterException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CartModel {
    private Long id;
    private String userId;
    private List<CartItemModel> items;
    private double total;
    private LocalDateTime lastUpdated;
    private String status;
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String ABANDONED_STATUS = "ABANDONED";
    private static final String COMPLETED_STATUS = "COMPLETED";

    // Default constructor for frameworks
    public CartModel() {
        this.items = new ArrayList<>();
        this.total = 0.0;
        this.lastUpdated = LocalDateTime.now();
        this.status = ACTIVE_STATUS;
    }

    public CartModel(String userId) {
        validateUserId(userId);
        this.userId = userId;
        this.items = new ArrayList<>();
        this.total = 0.0;
        this.lastUpdated = LocalDateTime.now();
        this.status = ACTIVE_STATUS;
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new InvalidParameterException("User ID cannot be empty");
        }
    }

    public void addItem(CartItemModel newItem) {
        validateCartStatus();
        if (newItem == null) {
            throw new InvalidParameterException("Cart item cannot be null");
        }

        boolean itemExists = items.stream()
                .anyMatch(item -> item.getArticleId().equals(newItem.getArticleId()));

        if (itemExists) {
            throw new DuplicateArticleException("Article already exists in cart");
        }

        items.add(newItem);
        updateTotalAndTimestamp();
    }

    public void updateItemQuantity(Long articleId, int newQuantity) {
        validateCartStatus();
        CartItemModel item = findItemByArticleId(articleId)
                .orElseThrow(() -> new CartItemNotFoundException("Article not found in cart"));

        item.updateQuantity(newQuantity);
        updateTotalAndTimestamp();
    }

    public void removeItem(Long articleId) {
        validateCartStatus();
        boolean removed = items.removeIf(item -> item.getArticleId().equals(articleId));
        if (!removed) {
            throw new CartItemNotFoundException("Article not found in cart");
        }
        updateTotalAndTimestamp();
    }

    private void validateCartStatus() {
        if (!ACTIVE_STATUS.equals(status)) {
            throw new InvalidCartOperationException("Cart is not active");
        }
    }

    private Optional<CartItemModel> findItemByArticleId(Long articleId) {
        return items.stream()
                .filter(item -> item.getArticleId().equals(articleId))
                .findFirst();
    }

    private void updateTotalAndTimestamp() {
        this.total = items.stream()
                .mapToDouble(CartItemModel::getSubtotal)
                .sum();
        this.lastUpdated = LocalDateTime.now();
    }

    public void clear() {
        validateCartStatus();
        items.clear();
        updateTotalAndTimestamp();
    }

    public void abandon() {
        if (!items.isEmpty() && ACTIVE_STATUS.equals(status)) {
            this.status = ABANDONED_STATUS;
            this.lastUpdated = LocalDateTime.now();
        }
    }

    public void complete() {
        if (!items.isEmpty() && ACTIVE_STATUS.equals(status)) {
            this.status = COMPLETED_STATUS;
            this.lastUpdated = LocalDateTime.now();
        }
    }

    // Getters
    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public List<CartItemModel> getItems() { return new ArrayList<>(items); }
    public double getTotal() { return total; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public String getStatus() { return status; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setItems(List<CartItemModel> items) {
        this.items = items != null ? items : new ArrayList<>();
        updateTotalAndTimestamp();
    }
    public void setTotal(double total) { this.total = total; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    public void setStatus(String status) { this.status = status; }
}