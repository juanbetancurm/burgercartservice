package com.rockburger.cartservice.adapters.driving.http.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartItemResponse {
    private Long id;
    private Long articleId;
    private String articleName;
    private int quantity;
    private double price;
    private double subtotal;
}