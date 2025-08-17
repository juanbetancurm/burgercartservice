package com.rockburger.cartservice.adapters.driven.jpa.mysql.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cart_items")
public class CartItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    @ToString.Exclude
    private CartEntity cart;

    @NotNull
    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @NotNull
    @Size(max = 100)
    @Column(name = "article_name", nullable = false)
    private String articleName;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private int quantity;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private double price;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private double subtotal;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    /**
     * JPA lifecycle callback for entity creation.
     * Sets creation and update timestamps, and calculates subtotal.
     */
    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.subtotal = this.quantity * this.price;
    }

    /**
     * JPA lifecycle callback for entity updates.
     * Updates the timestamp and recalculates subtotal.
     */
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        this.subtotal = this.quantity * this.price;
    }

    /**
     * Manual method to recalculate subtotal when needed.
     * This is not a JPA callback to avoid conflicts.
     */
    public void calculateSubtotal() {
        this.subtotal = this.quantity * this.price;
    }

    /**
     * Setter for quantity that automatically recalculates subtotal.
     */
    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.subtotal = this.quantity * this.price;
    }

    /**
     * Setter for price that automatically recalculates subtotal.
     */
    public void setPrice(double price) {
        this.price = price;
        this.subtotal = this.quantity * this.price;
    }
}