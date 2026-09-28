package com.eventticketplatform.bookingservice.controller;

import com.eventticketplatform.bookingservice.dto.BookingRequestDto;
import com.eventticketplatform.bookingservice.dto.BookingResponseDto;
import com.eventticketplatform.bookingservice.dto.RefundSummaryDto;
import com.eventticketplatform.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponseDto> createBooking(@Valid @RequestBody BookingRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(dto));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BookingResponseDto>> getBookingsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(bookingService.getBookingsByUser(userId));
    }

    @GetMapping("/organizer/{organizerId}")
    public ResponseEntity<List<BookingResponseDto>> getBookingsByOrganizer(@PathVariable Long organizerId) {
        return ResponseEntity.ok(bookingService.getBookingsByOrganizer(organizerId));
    }

    @GetMapping
    public ResponseEntity<List<BookingResponseDto>> getAllBookings() {
        return ResponseEntity.ok(bookingService.getAllBookings());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponseDto> getBookingById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<BookingResponseDto> confirmBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.confirmBooking(id));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<BookingResponseDto> cancelBookingPut(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponseDto> cancelBookingPost(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<BookingResponseDto> cancelBookingDelete(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    /** Process a refund — validates 6-hour window and 24-hour pre-event cutoff. */
    @PutMapping("/{id}/refund")
    public ResponseEntity<BookingResponseDto> requestRefund(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.requestRefund(id));
    }

    /** Admin: all refund requests with user name and email for the refund payout table. */
    @GetMapping("/refunds")
    public ResponseEntity<List<RefundSummaryDto>> getRefundedBookings() {
        return ResponseEntity.ok(bookingService.getRefundedBookings());
    }
}