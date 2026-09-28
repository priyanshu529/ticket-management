package com.eventticketplatform.paymentservice.controller;

import com.eventticketplatform.paymentservice.dto.*;
import com.eventticketplatform.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping({ "", "/create-order" })
    public ResponseEntity<RazorpayOrderResponseDto> createOrder(
            @Valid @RequestBody CreateOrderRequestDto dto) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(paymentService.createOrder(dto));
    }

    @PostMapping("/verify")
    public ResponseEntity<PaymentResponseDto> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequestDto dto) {
        return ResponseEntity.ok(paymentService.verifyPayment(dto));
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<PaymentResponseDto> getPaymentByBooking(
            @PathVariable Long bookingId) {
        return ResponseEntity.ok(
                paymentService.getPaymentByBooking(bookingId)
        );
    }

    /**
     * Called by booking-service (via Feign) to mark a payment as REFUNDED
     * after the booking-service has validated the refund rules and cancelled the booking.
     * This is a simulated refund — no Razorpay API is called.
     */
    @PutMapping("/booking/{bookingId}/refund")
    public ResponseEntity<PaymentResponseDto> refundPayment(@PathVariable Long bookingId) {
        return ResponseEntity.ok(paymentService.refundPayment(bookingId));
    }
}