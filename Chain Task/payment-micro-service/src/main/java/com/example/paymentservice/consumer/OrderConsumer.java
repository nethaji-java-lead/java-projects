
package com.example.paymentservice.consumer;

import com.example.paymentservice.event.OrderCreatedEvent;
import com.example.paymentservice.event.PaymentEvent;
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


/*
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order.created.DLT --from-beginning --property print.key=true --property print.timestamp=true
*/


@Service
@RequiredArgsConstructor
@Slf4j
public class OrderConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${payment.gateway.url:http://localhost:8089}")
    private String gatewayUrl;

    @Value("${payment.retry.max-attempts:3}")
    private int maxRetryAttempts;

    private String messageReceived;

    private String paymentFailureReason;

    @KafkaListener(topics = "order.created", groupId = "payment-group")
    public void consumeOrderCreatedEvent(String message)
    {
        messageReceived = message;
        log.info("received order.created event: {}", message);
        OrderCreatedEvent event;

        try{
            log.info("kafka message: {}", message);
            event = objectMapper.readValue(message, OrderCreatedEvent.class);
        } catch(Exception e)
        {
            log.error("JSON parsing error");
            throw e;
        }

        Long orderId = event.orderId();
        Payment payment = paymentService.getOrCreatePayment(event);

        if(payment.getStatus().equals("SUCCESS") | payment.getStatus().equals("FAILED"))
        {
            log.info("Payment is already processed");
            return;
        }
        processPayment(orderId, payment, event);
    }

    private void processPayment(Long orderId, Payment payment, OrderCreatedEvent event)
    {
        try {

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));

        RestClient client = RestClient.builder()
                .baseUrl(gatewayUrl)
                .requestFactory(factory)
                .build();

        String scenario = "";
        if(event.customerId()==1)
        {
            scenario = "success";
        } else if(event.customerId()==2) {
            scenario = "permanent failure";
        } else if(event.customerId()==3) {
            scenario = "timeout";
        } else {
            scenario = "crash";
        }

        String response = client.post()
                .uri("/api/payments/charge")
                .header("X-Test-Scenario", scenario)
                .header("Idempotency-Key", "order-"+orderId)
                .retrieve().body(String.class);

        if(response!=null && response.contains("SUCCESS"))
        {
            if("crash".equals(scenario))
            {
                paymentService.markUnknown(payment);
                paymentFailureReason = "Payment Gateway Crashed";
                publish(event, "PAYMENT_FAILED");
                log.error("Payment marked as Unknown");
                Runtime.getRuntime().halt(1);
            }
            paymentService.markSuccess(payment);
            publish(event, "PAYMENT_COMPLETED");
            return;
        } else {
            paymentService.markFailed(payment);
            paymentFailureReason = "Permanent Failure";
            publish(event, "PAYMENT_FAILED");
        }
        }catch (ResourceAccessException e)
        {
            if(payment.getRetryCount() < maxRetryAttempts)
            {
                processRetry(payment, event);
            } else {
                paymentService.markFailed(payment);
                paymentFailureReason = "Payment Gateway Timeout";
                kafkaTemplate.send("order.created.DLT", String.valueOf(orderId), messageReceived);
                publish(event, "PAYMENT_FAILED");
            }
        }

    }

    private void publish(OrderCreatedEvent event, String status)
    {
        PaymentEvent paymentEvent = new PaymentEvent(
                event.orderId(), event.orderNumber(), event.customerId(), event.customerEmail(), event.totalAmount(), status, paymentFailureReason);
        kafkaTemplate.send("payment.event", String.valueOf(event.orderId()), objectMapper.writeValueAsString(paymentEvent));
    }

    private void processRetry(Payment payment, OrderCreatedEvent event)
    {
        payment = paymentService.recordTimeout(payment);
        log.warn("Payment gateway timeout. Retry count {}", payment.getRetryCount());
        processPayment(event.orderId(), payment, event);
    }
}