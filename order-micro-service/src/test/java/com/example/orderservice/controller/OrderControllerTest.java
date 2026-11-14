package com.example.orderservice.controller;

import com.example.orderservice.dto.OrderDTO;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.enums.EventStatus;
import com.example.orderservice.enums.EventType;
import com.example.orderservice.enums.OrderStatus;
import com.example.orderservice.exception.OrderEventNotFoundException;
import com.example.orderservice.exception.OrderException;
import com.example.orderservice.model.OrderEvent;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.service.OutboxPublisherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    private static final String VALID_ORDER_REQUEST = """
            {
              "customerId": 7,
              "productId": "product-1",
              "productCode": "SKU-1",
              "quantity": 2,
              "totalAmount": 25.50
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OutboxPublisherService outboxPublisherService;

    @Test
    void sayHiReturnsGreeting() throws Exception {
        mockMvc.perform(get("/api/public/hi"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hi User!"));
    }

    @Test
    void createOrderReturnsCreatedOrderAndGeneratedId() throws Exception {
        OrderDTO order = order(42L);
        when(orderService.createOrder(
                new OrderRequest(7L, "product-1", "SKU-1", 2, new BigDecimal("25.50")),
                "request-123"
        )).thenReturn(order);

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "request-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order created successfully"))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-00000042"))
                .andExpect(jsonPath("$.data.customerId").value(7))
                .andExpect(jsonPath("$.data.totalAmount").value(25.50))
                .andExpect(jsonPath("$.data.status").value("CREATED"));

        verify(orderService).createOrder(
                new OrderRequest(7L, "product-1", "SKU-1", 2, new BigDecimal("25.50")),
                "request-123"
        );
    }

    @Test
    void getOrderReturnsOrderDetails() throws Exception {
        when(orderService.readOrder(42L)).thenReturn(order(42L));

        mockMvc.perform(get("/api/orders/42"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-00000042"))
                .andExpect(jsonPath("$.data.customerId").value(7))
                .andExpect(jsonPath("$.data.totalAmount").value(25.50));

        verify(orderService).readOrder(42L);
    }

    @Test
    void getOrderEventReturnsEventDetails() throws Exception {
        LocalDateTime createdAt = LocalDateTime.parse("2026-01-01T10:15:30");
        OrderEvent event = new OrderEvent();
        event.setId(101L);
        event.setOrderId(42L);
        event.setEventType(EventType.ORDER_CREATED);
        event.setTopic("order.created");
        event.setPayload("{\"orderId\":42}");
        event.setStatus(EventStatus.PENDING);
        event.setCreatedAt(createdAt);
        event.setRetryCount(0);
        when(outboxPublisherService.getOrderEventByOrderId(42L)).thenReturn(Optional.of(event));

        mockMvc.perform(get("/api/orders/42/events"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order event found"))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.orderId").value(42))
                .andExpect(jsonPath("$.data.eventType").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.data.topic").value("order.created"))
                .andExpect(jsonPath("$.data.payload").value("{\"orderId\":42}"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.retryCount").value(0));

        verify(outboxPublisherService).getOrderEventByOrderId(42L);
    }

    @Test
    void getOrderEventForUnknownOrderReturnsNotFoundResponse() throws Exception {
        when(outboxPublisherService.getOrderEventByOrderId(404L))
                .thenThrow(new OrderEventNotFoundException("Order event not found for orderId: 404"));

        mockMvc.perform(get("/api/orders/404/events"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Order event not found for orderId: 404"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void createOrderRejectsMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Required header is missing: Idempotency-Key"));

        verifyNoInteractions(orderService);
    }

    @Test
    void createOrderRejectsInvalidRequestFields() throws Exception {
        String invalidRequest = """
                {
                  "customerId": null,
                  "productId": " ",
                  "productCode": "",
                  "quantity": 0,
                  "totalAmount": 25.50
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "request-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.customerId").value("Customer ID is required"))
                .andExpect(jsonPath("$.data.productId").value("Product ID is required"))
                .andExpect(jsonPath("$.data.productCode").value("Product code is required"))
                .andExpect(jsonPath("$.data.quantity").value("Quantity must be at least 1"));

        verifyNoInteractions(orderService);
    }

    @Test
    void createOrderRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "request-malformed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request body"));

        verifyNoInteractions(orderService);
    }

    @Test
    void createOrderReturnsInternalServerErrorWhenServiceFailsUnexpectedly() throws Exception {
        when(orderService.createOrder(
                new OrderRequest(7L, "product-1", "SKU-1", 2, new BigDecimal("25.50")),
                "request-error"
        )).thenThrow(new IllegalStateException("database unavailable"));

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "request-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER_REQUEST))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Failed to create order"));
    }

    @Test
    void getOrderForUnknownIdReturnsNotFoundResponse() throws Exception {
        when(orderService.readOrder(404L))
                .thenThrow(new OrderException("Order not found for ID: 404"));

        mockMvc.perform(get("/api/orders/404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Order not found for ID: 404"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getOrderRejectsMalformedId() throws Exception {
        mockMvc.perform(get("/api/orders/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: id"));

        verifyNoInteractions(orderService);
    }

    private static OrderDTO order(Long id) {
        LocalDateTime timestamp = LocalDateTime.parse("2026-01-01T10:15:30");
        return new OrderDTO(
                id,
                "ORD-00000042",
                7L,
                new BigDecimal("25.50"),
                OrderStatus.CREATED,
                timestamp,
                timestamp
        );
    }
}
