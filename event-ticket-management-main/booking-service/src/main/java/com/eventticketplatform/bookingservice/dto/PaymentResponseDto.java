package com.eventticketplatform.bookingservice.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Minimal DTO used only for deserialising the payment-service refund response. */
@Data
public class PaymentResponseDto {
    private Long id;
    private Long bookingId;
    private Long userId;
    private BigDecimal amount;
    private String status;
    private String paymentMethod;
    private LocalDateTime paymentDate;
    private LocalDateTime refundDate;
    private String transactionId;
    private String razorpayOrderId;
    private String razorpayPaymentId;
}