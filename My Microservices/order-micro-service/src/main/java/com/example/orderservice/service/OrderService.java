package com.example.orderservice.service;


import com.example.orderservice.dto.OrderDTO;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.enums.EventStatus;
import com.example.orderservice.enums.EventType;
import com.example.orderservice.event.OrderCreatedEvent;
import com.example.orderservice.exception.OrderException;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderEvent;
import com.example.orderservice.model.User;
import com.example.orderservice.repository.OrderEventRepository;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    public static final String ORDER_CREATED_EVENT = "order.created";

    private final OrderRepository orderRepository;

    private final UserRepository userRepository;

    private final OrderEventRepository orderEventRepository;

    private final JsonMapper jsonMapper;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository, OrderEventRepository orderEventRepository, JsonMapper jsonMapper) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.orderEventRepository = orderEventRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public OrderDTO createOrder(OrderRequest request, String idempotencyKey) {
        Order order = new Order();

        // 1. Check if request was already processed
        Optional<Order> existingOrder =
                orderRepository.findByIdempotencyKey(idempotencyKey);

        if (existingOrder.isPresent()) {
            order =  existingOrder.get();
        } else {
            // 2. Create new order
            order.setOrderNumber(generateOrderNumber());
            order.setCustomerId(request.customerId());
            order.setTotalAmount(request.totalAmount());
            order.setIdempotencyKey(idempotencyKey);
        }

        order = orderRepository.save(order);

        // Create OrderEvent - Transactional Outbox Database
        createOrderEvent(order);

       return mapToOrderDTO(order);
    }

    @Transactional
    public OrderDTO readOrder(Long id) {
        Optional<Order> existingOrder = orderRepository.findById(id);
        return existingOrder
                .map(this::mapToOrderDTO)
                .orElseThrow(() -> new OrderException("Order not found for ID: "+id));
    }

    private void createOrderEvent(Order order) {
        orderEventRepository
                .findByAggregateId(order.getId())
                .ifPresentOrElse(
                        existingEvent -> {
                            // Event already exists - do nothing
                            throw new OrderException("Order Event already exists!");
                        },
                        () -> {
                            OrderEvent orderEvent = new OrderEvent();
                            orderEvent.setAggregateId(order.getId());
                            orderEvent.setEventType(EventType.ORDER_CREATED);
                            orderEvent.setTopic(ORDER_CREATED_EVENT);
                            orderEvent.setPayload(
                                    convertToJson(createOrderCreatedEvent(order))
                            );
                            orderEvent.setStatus(EventStatus.PENDING);

                            orderEventRepository.save(orderEvent);
                        }
                );
    }

    private OrderCreatedEvent createOrderCreatedEvent(Order order) {

        Optional<User> optionalUser = userRepository.findById(order.getCustomerId());

        User user = optionalUser.orElseThrow(() ->
                new RuntimeException("User not found: " + order.getCustomerId()));


/*        User user = User.builder()
                .name("John Doe")
                .address("123 Main Street, Chennai")
                .shippingAddress("45 Anna Nagar, Chennai")
                .email("nethaji.techlead@gmail.com")
                .phone("8056134756")
                .emailNotifications(true)
                .smsNotifications(true)
                .build();*/

        return new OrderCreatedEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                user.getEmail(),
                user.getPhone(),
                user.isEmailNotifications(),
                user.isSmsNotifications(),
                order.getTotalAmount()
        );
    }

    private String convertToJson(OrderCreatedEvent event) {
        try {
            return jsonMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new OrderException("Failed to serialize OrderCreatedEvent", e);
        }
    }



    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }

    private OrderDTO mapToOrderDTO(Order order)
    {
        return new OrderDTO(order.getId()
                , order.getOrderNumber(), order.getCustomerId(), order.getTotalAmount(), order.getStatus()
        , order.getCreatedAt(), order.getUpdatedAt());
    }
}
