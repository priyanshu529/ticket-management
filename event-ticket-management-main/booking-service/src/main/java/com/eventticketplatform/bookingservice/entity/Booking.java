package com.eventticketplatform.bookingservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private LocalDateTime bookingDate = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    @Column
    private LocalDateTime expiresAt;

    /**
     * Set when the booking transitions to CONFIRMED (i.e. payment is verified).
     * Used to enforce the 6-hour refund eligibility window.
     */
    @Column
    private LocalDateTime confirmedAt;

    /**
     * Set when a refund is successfully processed via requestRefund().
     * Null for bookings that were never refunded.
     * Used as the authoritative marker to populate the admin refund table.
     */
    @Column
    private LocalDateTime refundedAt;
}
