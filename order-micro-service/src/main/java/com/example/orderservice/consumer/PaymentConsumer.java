package com.example.orderservice.consumer;

import com.example.orderservice.enums.OrderStatus;
import com.example.orderservice.event.OrderCancelledEvent;
import com.example.orderservice.event.PaymentEvent;
import com.example.orderservice.exception.OrderException;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.User;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.UserRepository;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.service.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {

    private final ObjectMapper objectMapper;

    private final OrderRepository orderRepository;

    private final OutboxPublisherService publisherService;

    private final UserRepository userRepository;

    @KafkaListener(
            topics = "payment.event",
            groupId = "order-service"
    )
    public void consumePaymentEvent(String message) {
        try {
            PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);
            Optional<Order> optionalOrder = orderRepository.findById(event.orderId());
            log.info("Received Payment Event: {}", event);
            if (optionalOrder.isPresent()) {
                Order order = optionalOrder.get();
                if(order.getStatus()==OrderStatus.PAID || order.getStatus()==OrderStatus.CANCELLED)
                {
                    log.info("Order is already in PAID/CANCELLED status. Skipping the Payment Event!");
                    return;
                }
                if(event.status().equals("PAYMENT_COMPLETED"))
                {
                    order.setStatus(OrderStatus.PAID);
                } else if(event.status().equals("PAYMENT_FAILED")) {
                    if(event.failureReason()!=null && event.failureReason().equals("Permanent Failure")) {
                        order.setStatus(OrderStatus.CANCELLED);

                        User user = userRepository.findById(order.getUser().getUserId()).orElseThrow();
                        publisherService.publishOrderCancelledEvent
                                (new OrderCancelledEvent(order.getId(),
                                        order.getOrderNumber(), order.getUser().getUserId(), user.getEmail()));
                    } else {
                        order.setStatus(OrderStatus.PAYMENT_FAILED);
                    }
                }
                orderRepository.save(order);
            } else {
                throw new OrderException("Order not found for ID: " + event.orderId());
            }
        } catch (Exception e) {
            throw new OrderException("Failed to process payment.completed event", e);
        }
    }
}
