package com.example.order.service;

import com.example.order.dto.OrderRequest;
import com.example.order.entity.Order;

public interface OrderService {
    Order createOrder(OrderRequest orderRequest);
}