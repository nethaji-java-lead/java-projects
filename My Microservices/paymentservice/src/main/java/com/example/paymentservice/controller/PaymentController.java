package com.example.paymentservice.controller;

import com.example.paymentservice.model.Payment;
import com.example.paymentservice.request.PaymentCreateRequest;
import com.example.paymentservice.response.PaymentResponseDTO;
import com.example.paymentservice.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public ResponseEntity<List<Payment>> getPayment()
    {
        List<Payment> payments = paymentService.fetchAllPayments();
        return ResponseEntity.ok(payments);
    }

    @PostMapping("/create")
    public ResponseEntity<Payment> createPayment(@RequestBody PaymentCreateRequest paymentCreateRequest) {
        Payment createdPayment = paymentService.createPayment(paymentCreateRequest);
        return new ResponseEntity<>(createdPayment, HttpStatus.CREATED);
    }
}