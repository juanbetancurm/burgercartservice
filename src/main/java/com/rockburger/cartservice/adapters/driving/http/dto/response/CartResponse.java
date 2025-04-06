package com.rockburger.cartservice.adapters.driving.http.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartResponse {
    private Long id;
    private String userId;
    private List<CartItemResponse> items;
    private double total;
    private LocalDateTime lastUpdated;
    private String status;
}