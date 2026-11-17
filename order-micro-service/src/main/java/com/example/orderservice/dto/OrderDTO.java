package com.example.orderservice.dto;

import com.example.orderservice.enums.OrderStatus;
import com.example.orderservice.model.Product;
import com.example.orderservice.model.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderDTO(
        Long id,
        String orderNumber,
        User user,
        Product product,
        Integer quantity,
        BigDecimal totalAmount,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}