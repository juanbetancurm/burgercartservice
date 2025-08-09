package com.rockburger.cartservice.adapters.driven.jpa.mysql.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "carts")
public class CartEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private String userId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<CartItemEntity> items = new ArrayList<>();

    @Column(nullable = false)
    private double total;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(nullable = false, length = 20)
    private String status;

    @Version
    private Long version;

    // Custom constructor for proper initialization
    public CartEntity(String userId, String status) {
        this.userId = userId;
        this.status = status;
        this.items = new ArrayList<>();
        this.total = 0.0;
        this.lastUpdated = LocalDateTime.now();
    }

    // Helper methods to manage bidirectional relationship properly
    public void addItem(CartItemEntity item) {
        if (item == null) {
            return;
        }

        // Remove if already exists (based on articleId)
        items.removeIf(existingItem -> existingItem.getArticleId().equals(item.getArticleId()));

        // Add new item
        items.add(item);
        item.setCart(this);

        // Recalculate total
        recalculateTotal();
    }

    public void removeItem(CartItemEntity item) {
        if (item == null) {
            return;
        }

        items.remove(item);
        item.setCart(null);
        recalculateTotal();
    }

    public void removeItemByArticleId(Long articleId) {
        items.removeIf(item -> item.getArticleId().equals(articleId));
        recalculateTotal();
    }

    public void clearItems() {
        for (CartItemEntity item : items) {
            item.setCart(null);
        }
        items.clear();
        this.total = 0.0;
        this.lastUpdated = LocalDateTime.now();
    }

    private void recalculateTotal() {
        this.total = items.stream()
                .mapToDouble(CartItemEntity::getSubtotal)
                .sum();
        this.lastUpdated = LocalDateTime.now();
    }

    // Override setItems to ensure proper relationship management
    public void setItems(List<CartItemEntity> items) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }

        // Clear existing items
        this.items.clear();

        // Add new items with proper relationship setup
        if (items != null) {
            for (CartItemEntity item : items) {
                addItem(item);
            }
        }
    }

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.lastUpdated = LocalDateTime.now();
        recalculateTotal();
    }
}