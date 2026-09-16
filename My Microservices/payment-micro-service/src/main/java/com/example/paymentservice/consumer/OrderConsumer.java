package com.example.paymentservice.consumer;

import com.example.paymentservice.enums.OrderStatus;
import com.example.paymentservice.event.OrderCreatedEvent;
import com.example.paymentservice.event.PaymentCompletedEvent;
import com.example.paymentservice.repository.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderConsumer {

    private static final String ORDER_CREATED_EVENT = "order.created";
    private static final String PAYMENT_COMPLETED_EVENT = "payment.completed";

    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderConsumer(
            ObjectMapper objectMapper,
            OrderRepository orderRepository,
            KafkaTemplate<String, String> kafkaTemplate) {

        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = ORDER_CREATED_EVENT,
            groupId = "payment-service"
    )
    @Transactional
    public void consumeOrderCreatedEvent(String message) {

        try {

            System.out.println(
                    "Received order.created event: " + message
            );

            OrderCreatedEvent event =
                    objectMapper.readValue(
                            message,
                            OrderCreatedEvent.class
                    );

            Long orderId = event.orderId();

            // 1. Process payment
            boolean paymentSuccessful = processPayment(event);

            if (!paymentSuccessful) {
                return;
            }

            // 2. Update Order business status
            orderRepository.updateStatus(
                    orderId,
                    OrderStatus.PAYMENT_COMPLETED
            );

            // 3. Create payment.completed event
            PaymentCompletedEvent paymentEvent =
                    new PaymentCompletedEvent(
                            event.orderId(),
                            event.orderNumber(),
                            event.customerId(),
                            event.customerEmail(),
                            event.customerPhone(),
                            event.totalAmount(),
                            "PAYMENT_COMPLETED"
                    );

            // 4. Publish payment.completed
            String payload =
                    objectMapper.writeValueAsString(paymentEvent);

            kafkaTemplate.send(
                    PAYMENT_COMPLETED_EVENT,
                    String.valueOf(orderId),
                    payload
            );

        } catch (Exception e) {

            System.err.println(
                    "Failed to process order.created event: " + message
            );

            throw new RuntimeException(e);
        }
    }

    private boolean processPayment(OrderCreatedEvent event) {

        // Actual payment gateway integration here

        return true;
    }
}