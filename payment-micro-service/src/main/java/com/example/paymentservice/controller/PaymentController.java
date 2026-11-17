package com.example.paymentservice.controller;

import com.example.paymentservice.event.OrderCreatedEvent;
import com.example.paymentservice.event.PaymentEvent;
import com.example.paymentservice.exception.PaymentNotFoundException;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/payment")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {
    private final KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper;

    @PostMapping("/publish")
    public void publishOrderEvent(@RequestBody PaymentEvent paymentEvent)
    {
        try{
            kafkaTemplate.send("payment.event", String.valueOf(paymentEvent.orderId()),
                    objectMapper.writeValueAsString(paymentEvent));
        } catch (Exception e){
            log.error("Error : {}", e.getMessage());
        }

    }
}
