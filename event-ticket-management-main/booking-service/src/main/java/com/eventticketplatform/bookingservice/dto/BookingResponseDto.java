package com.eventticketplatform.bookingservice.dto;

import com.eventticketplatform.bookingservice.entity.BookingStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BookingResponseDto {
    private Long id;
    private Long userId;
    private Long eventId;
    private String eventName;
    private String eventVenue;
    private String eventCity;
    private LocalDateTime eventDate;
    private Integer quantity;
    private BigDecimal totalAmount;
    private LocalDateTime bookingDate;
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime refundedAt;
    private BookingStatus status;
}

