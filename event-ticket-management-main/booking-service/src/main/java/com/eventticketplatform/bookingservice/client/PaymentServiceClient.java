package com.eventticketplatform.bookingservice.client;

import com.eventticketplatform.bookingservice.dto.PaymentResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * Feign client for calling the payment-service to mark a payment as REFUNDED
 * after a successful refund request from the booking-service.
 */
@FeignClient(name = "payment-service")
public interface PaymentServiceClient {

    @PutMapping("/api/payments/booking/{bookingId}/refund")
    PaymentResponseDto refundPayment(@PathVariable("bookingId") Long bookingId);
}