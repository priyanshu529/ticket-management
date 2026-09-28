package com.eventticketplatform.bookingservice.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Projection returned by GET /api/bookings/refunds (admin use).
 * Combines booking data with the user name and email fetched from user-service,
 * so admins can immediately identify who to send the refund to.
 */
@Data
public class RefundSummaryDto {
    private Long bookingId;
    private Long eventId;
    private String eventName;
    private String eventVenue;
    private String eventCity;
    private LocalDateTime eventDate;

    // User info from user-service
    private Long userId;
    private String userName;
    private String userEmail;

    private Integer quantity;
    private BigDecimal totalAmount;
    private LocalDateTime confirmedAt;
    private LocalDateTime refundedAt;
}