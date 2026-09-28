package com.eventticketplatform.bookingservice.repository;

import com.eventticketplatform.bookingservice.entity.Booking;
import com.eventticketplatform.bookingservice.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserId(Long userId);

    List<Booking> findByEventIdIn(List<Long> eventIds);

    List<Booking> findByStatusAndExpiresAtBefore(BookingStatus status, LocalDateTime dateTime);

    /** Returns all bookings that were cancelled via a refund request, newest first. */
    List<Booking> findByRefundedAtIsNotNullOrderByRefundedAtDesc();
}