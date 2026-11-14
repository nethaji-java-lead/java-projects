
package com.example.paymentservice.consumer;

import com.example.paymentservice.event.OrderCreatedEvent;
import com.example.paymentservice.event.PaymentCompletedEvent;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${payment.gateway.url:http://localhost:8089}")
    private String gatewayUrl;

    @Value("${payment.demo.scenario:success}")
    private String scenario;

    @Value("${payment.retry.max-attempts:3}")
    private int maxRetryAttempts;

    private String messageReceived;

    @KafkaListener(topics = "order.created", groupId = "payment-group")
    public void consumeOrderCreatedEvent(String message) throws Exception {
        messageReceived = message;
        log.info("Received order.created event: {}", message);
        OrderCreatedEvent event;

        try {
            log.info("Kafka Message: {}", message);
            event = objectMapper.readValue(message, OrderCreatedEvent.class);
        } catch (Exception e) {
            log.error("JSON Deserialization failed: {}", e.getMessage(), e);
            throw e;
        }

        Long orderId = event.orderId();
        Payment payment = paymentService.getOrCreatePayment(event);

        log.info("Payment Created: {}", payment);

        // 1. Skip duplicate or already failed payment
        if (payment.getStatus().equals("COMPLETED") ||
                payment.getStatus().equals("FAILED")) {
            log.info("Payment already processed for order {}", orderId);
            return;
        }
        processPayment(orderId, payment, event);
    }

    private void processPayment(Long orderId, Payment payment, OrderCreatedEvent event) throws Exception {
        try {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(2));
            factory.setReadTimeout(Duration.ofSeconds(3));

            RestClient client = RestClient.builder()
                    .baseUrl(gatewayUrl)
                    .requestFactory(factory)
                    .build();

            String response = client.post()
                    .uri("/api/payments/charge")
                    .header("X-Test-Scenario", scenario)
                    .header("Idempotency-Key", "order-" + orderId)
                    .retrieve()
                    .body(String.class);

            assert response != null;
            if(response.contains("SUCCESS")) {
                log.info("Payment processed successfully for order {}", orderId);
                paymentService.markCompleted(payment);
                publish("payment.completed", event, "PAYMENT_COMPLETED");
            } else {
                log.error("Payment gateway error for order {}. Response: {}", orderId, response);
                paymentService.markFailed(payment);
                publish("payment.failed", event, "PAYMENT_FAILED");
            }

        }catch (ResourceAccessException e) {
            if(payment.getRetryCount() < maxRetryAttempts) {
                processRetry(payment, event);
            } else {
                paymentService.markFailed(payment);
                log.error("Payment gateway timeout for order {}. Max retries reached.", orderId);
                // Send original order event to Dead Letter Topic
                kafkaTemplate.send(
                        "order.created.DLT",
                        String.valueOf(orderId),
                        messageReceived
                );

                // Notify Order Service
                publish("payment.failed", event, "PAYMENT_FAILED");
            }
        }
    }

    private void processRetry(Payment payment, OrderCreatedEvent event) throws Exception {
        payment = paymentService.recordTimeout(payment);
        log.warn("Payment gateway timeout for order {}. Retry count: {}",
                event.orderId(), payment.getRetryCount());
        processPayment(event.orderId(), payment, event);
        publish("payment.retry", event, "PAYMENT_RETRY");
    }

    private void publish(String topic, OrderCreatedEvent event,
                         String status) throws Exception {

        PaymentCompletedEvent paymentEvent = new PaymentCompletedEvent(
                event.orderId(),
                event.orderNumber(),
                event.customerId(),
                event.customerEmail(),
                event.customerPhone(),
                event.totalAmount(),
                status
        );

        kafkaTemplate.send(
                topic,
                String.valueOf(event.orderId()),
                objectMapper.writeValueAsString(paymentEvent)
        );
    }
}