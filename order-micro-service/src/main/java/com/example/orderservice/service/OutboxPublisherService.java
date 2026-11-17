package com.example.orderservice.service;

import com.example.orderservice.enums.EventStatus;
import com.example.orderservice.event.OrderCancelledEvent;
import com.example.orderservice.event.OrderCreatedEvent;
import com.example.orderservice.exception.OrderEventNotFoundException;
import com.example.orderservice.model.OrderEvent;
import com.example.orderservice.repository.OrderEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private final OrderEventRepository orderEventRepository;

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper;

    @Scheduled(fixedRate = 10000)
    @Transactional
    public void publishPendingEvents() {

        log.info("Finding Pending Events to process");

        List<OrderEvent> events =
                orderEventRepository.findByStatus(EventStatus.PENDING);
        for (OrderEvent event : events) {
            try {
                log.info(
                        "Publishing outbox event. eventId={}, eventType={}, topic={}",
                        event.getId(),
                        event.getEventType(),
                        event.getTopic()
                );
                kafkaTemplate.send(event.getTopic(), String.valueOf(event.getOrderId()), event.getPayload());
                event.setStatus(EventStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
                orderEventRepository.save(event);

                log.info(
                        "Successfully published outbox event. eventId={}, eventType={}, topic={}",
                        event.getId(),
                        event.getEventType(),
                        event.getTopic()
                );
            } catch (Exception ex) {
                log.error(
                        "Failed to publish outbox event. eventId={}",
                        event.getId(),
                        ex
                );
            }
        }
    }

    public void publishOrderEvent(Long orderId)
    {
      OrderEvent event =
                orderEventRepository.findByOrderId(orderId).get();
        kafkaTemplate.send("order.created", String.valueOf(event.getOrderId()),
                event.getPayload());
        log.info("Published Order event: {}", event);
    }

    public void publishOrderCancelledEvent(OrderCancelledEvent event)
    {
        kafkaTemplate.send("order.cancelled",
                String.valueOf(event.orderId()), objectMapper.writeValueAsString(event));
    }
}