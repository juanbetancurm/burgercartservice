package com.rockburger.cartservice.adapters.driven.jpa.mysql.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

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

    @Version
    private Long version;

    // Pre-persist and pre-update hooks
    @PrePersist
    @PreUpdate
    public void calculateSubtotal() {
        this.subtotal = this.quantity * this.price;
    }
}