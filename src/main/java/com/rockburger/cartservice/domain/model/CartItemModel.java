package com.rockburger.cartservice.domain.model;

import com.rockburger.cartservice.domain.exception.InvalidParameterException;
import java.util.Objects;

public class CartItemModel {
    private Long id;
    private Long articleId;
    private String articleName;
    private int quantity;
    private double price;
    private double subtotal;

    // Default constructor for frameworks
    public CartItemModel() {
    }

    public CartItemModel(Long articleId, String articleName, int quantity, double price) {
        validateQuantity(quantity);
        validatePrice(price);
        validateArticle(articleId, articleName);

        this.articleId = articleId;
        this.articleName = articleName;
        this.quantity = quantity;
        this.price = price;
        calculateSubtotal();
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new InvalidParameterException("Quantity must be greater than zero");
        }
    }

    private void validatePrice(double price) {
        if (price < 0) {
            throw new InvalidParameterException("Price must be greater than or equal to zero");
        }
        if (Double.isInfinite(price) || Double.isNaN(price)) {
            throw new InvalidParameterException("Invalid price value");
        }
    }

    private void validateArticle(Long articleId, String articleName) {
        if (articleId == null) {
            throw new InvalidParameterException("Article ID cannot be null");
        }
        if (articleName == null || articleName.trim().isEmpty()) {
            throw new InvalidParameterException("Article name cannot be empty");
        }
    }

    public void updateQuantity(int newQuantity) {
        validateQuantity(newQuantity);
        this.quantity = newQuantity;
        calculateSubtotal();
    }

    private void calculateSubtotal() {
        this.subtotal = this.quantity * this.price;
    }

    // Getters
    public Long getId() { return id; }
    public Long getArticleId() { return articleId; }
    public String getArticleName() { return articleName; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public double getSubtotal() { return subtotal; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public void setArticleName(String articleName) { this.articleName = articleName; }
    public void setQuantity(int quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
        calculateSubtotal();
    }
    public void setPrice(double price) {
        validatePrice(price);
        this.price = price;
        calculateSubtotal();
    }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CartItemModel that = (CartItemModel) o;
        return Objects.equals(articleId, that.articleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(articleId);
    }
}