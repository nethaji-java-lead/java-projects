package com.example.paymentservice.event;

import java.math.BigDecimal;

public record PaymentEvent(
        Long orderId,
        String orderNumber,
        Long customerId,
        String customerEmail,
        BigDecimal amount,
        String status,
        String failureReason
) {}