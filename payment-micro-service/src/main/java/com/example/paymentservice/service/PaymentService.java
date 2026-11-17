package com.example.paymentservice.service;

import com.example.paymentservice.event.OrderCreatedEvent;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment getOrCreatePayment(OrderCreatedEvent event) {
        return paymentRepository.findByOrderId(event.orderId())
                .orElseGet(() -> {
                    Payment payment = new Payment();
                    payment.setOrderId(event.orderId());
                    payment.setAmount(event.totalAmount());
                    payment.setProductId(event.productId());
                    payment.setStatus("PENDING");
                    return paymentRepository.saveAndFlush(payment);
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment recordTimeout( Payment payment ) {
        payment.setRetryCount(payment.getRetryCount() + 1);
        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Payment payment) {
        payment.setStatus("SUCCESS");
        paymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Payment payment) {
        payment.setStatus("FAILED");
        paymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnknown(Payment payment) {
        payment.setStatus("UNKNOWN");
        paymentRepository.saveAndFlush(payment);
    }

}
