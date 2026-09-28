package com.eventticketplatform.paymentservice.dto;

import com.eventticketplatform.paymentservice.entity.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentResponseDto {
    private Long id;
    private Long bookingId;
    private Long userId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String paymentMethod;
    private LocalDateTime paymentDate;
    private String transactionId;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private LocalDateTime refundDate;
}