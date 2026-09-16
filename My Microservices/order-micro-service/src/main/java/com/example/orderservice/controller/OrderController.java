package com.example.orderservice.controller;


import com.example.orderservice.dto.OrderDTO;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.ApiResponse;
import com.example.orderservice.exception.OrderException;
import com.example.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/public/hi")
    public ResponseEntity<String> sayHi()
    {
        return ResponseEntity.ok("Hi User!");
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<OrderDTO>> createOrder(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OrderRequest request) {

        try {

            if(idempotencyKey==null || idempotencyKey.isBlank()){
                return ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.failure(
                                "Idempotency-Key is required as part of Request"
                        ));
            }
            OrderDTO order = orderService.createOrder(request, idempotencyKey);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success(
                            "Order created successfully",
                            order
                    ));

        } catch(OrderException e) {
            throw new OrderException(e.getMessage());
        }
        catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure(
                            "Failed to create order"
                    ));
        }
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<OrderDTO>> createOrder(@PathVariable Long id)
    {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(
                        "Order created successfully",
                        orderService.readOrder(id)));
    }

}
