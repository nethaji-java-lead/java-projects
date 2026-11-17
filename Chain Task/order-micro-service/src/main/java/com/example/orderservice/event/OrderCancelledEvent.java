package com.example.orderservice.event;

public record OrderCancelledEvent(Long orderId, String orderNumber,
                                   Long customerId,
                                   String customerEmail) {
}
