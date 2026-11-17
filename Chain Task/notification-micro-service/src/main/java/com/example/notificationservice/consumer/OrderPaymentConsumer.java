package com.example.notificationservice.consumer;

import com.example.orderservice.event.OrderCancelledEvent;
import com.example.orderservice.event.OrderCreatedEvent;
import com.example.orderservice.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderPaymentConsumer {

    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;

    @Value("${notification.mail.from}")
    private String fromEmail;

    @KafkaListener(
            topics = "order.created",
            groupId = "notification-service"
    )
    public void orderCreated(String message) throws Exception {

        OrderCreatedEvent event =
                objectMapper.readValue(message, OrderCreatedEvent.class);

        sendEmail(event.customerEmail(),
                "Order Created - " + event.orderNumber(),
                "Your order " + event.orderNumber() +
                        " has been created successfully.\n" +
                        "Amount: " + event.totalAmount()
        );

        log.info("Order created notification sent: {}", event.orderId());
    }

    @KafkaListener(
            topics = "order.cancelled",
            groupId = "notification-service"
    )
    public void orderCancelled(String message) throws Exception {

        OrderCancelledEvent event =
                objectMapper.readValue(message, OrderCancelledEvent.class);

        sendEmail(event.customerEmail(),
                "Order Cancelled - " + event.orderNumber(),
                "Your order " + event.orderNumber() +
                        " has been Cancelled");

        log.info("Order cancelled notification sent: {}", event.orderId());
    }

    @KafkaListener(
            topics = "payment.event",
            groupId = "notification-service"
    )
    public void paymentEvent(String message) throws Exception {

        PaymentEvent event =
                objectMapper.readValue(message, PaymentEvent.class);

        String body = "Payment Successfully done for the amount: "+event.amount()+" " +
                "for the Order Number:"+event.orderNumber();

        if(event.status().equals("PAYMENT_FAILED")) {
            body = "Payment of the amount: "+event.amount()+" " +
                    "is FAILED for the Order Number:"+event.orderNumber();
        }

        sendEmail(event.customerEmail(),
                "Payment Notification - " + event.orderNumber(), body
        );

        log.info("Payment notification sent: {}", event.orderId());
    }

    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("Email notification sent to: {}", to);
    }

}
