package com.eventticketplatform.bookingservice.service;

import com.eventticketplatform.bookingservice.client.EventServiceClient;
import com.eventticketplatform.bookingservice.client.PaymentServiceClient;
import com.eventticketplatform.bookingservice.client.UserServiceClient;
import com.eventticketplatform.bookingservice.dto.*;
import com.eventticketplatform.bookingservice.entity.Booking;
import com.eventticketplatform.bookingservice.entity.BookingStatus;
import com.eventticketplatform.bookingservice.entity.Refund;
import com.eventticketplatform.bookingservice.exception.ResourceNotFoundException;
import com.eventticketplatform.bookingservice.repository.BookingRepository;
import com.eventticketplatform.bookingservice.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RefundRepository refundRepository;
    private final EventServiceClient eventServiceClient;
    private final UserServiceClient userServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    @Transactional
    public BookingResponseDto createBooking(BookingRequestDto dto) {
        UserDto user = userServiceClient.getUserById(dto.getUserId());

        if (user != null && user.getRole() != null && "ORGANIZER".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("Organizers cannot book tickets. Please use an attendee account.");
        }

        if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
            throw new IllegalArgumentException("Booking quantity must be at least 1.");
        }

        EventDto event = eventServiceClient.updateSeats(dto.getEventId(), -dto.getQuantity());

        try {
            Booking booking = new Booking();
            booking.setUserId(dto.getUserId());
            booking.setEventId(dto.getEventId());
            booking.setQuantity(dto.getQuantity());
            BigDecimal price = event.getPrice() != null ? event.getPrice() : BigDecimal.ZERO;
            booking.setTotalAmount(price.multiply(BigDecimal.valueOf(dto.getQuantity())));
            booking.setBookingDate(LocalDateTime.now());
            booking.setStatus(BookingStatus.PENDING_PAYMENT);
            booking.setExpiresAt(LocalDateTime.now().plusMinutes(5));

            Booking saved = bookingRepository.save(booking);
            return toResponseDto(saved, event);
        } catch (Exception ex) {
            try {
                eventServiceClient.updateSeats(dto.getEventId(), dto.getQuantity());
            } catch (Exception rollbackEx) { /* ignore */ }
            throw new IllegalStateException("Failed to create booking hold: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public BookingResponseDto confirmBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            EventDto event = eventServiceClient.getEventById(booking.getEventId());
            return toResponseDto(booking, event);
        }

        if (booking.getStatus() == BookingStatus.EXPIRED || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Cannot confirm booking. It has already been " + booking.getStatus());
        }

        if (booking.getExpiresAt() != null && LocalDateTime.now().isAfter(booking.getExpiresAt())) {
            booking.setStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);
            eventServiceClient.updateSeats(booking.getEventId(), booking.getQuantity());
            throw new IllegalStateException("Booking reservation has expired. Held seats have been released.");
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);
        EventDto event = fetchEventSafe(saved.getEventId());
        return toResponseDto(saved, event);
    }

    public List<BookingResponseDto> getBookingsByUser(Long userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(b -> { EventDto event = fetchEventSafe(b.getEventId()); return toResponseDto(b, event); })
                .collect(Collectors.toList());
    }

    public List<BookingResponseDto> getBookingsByOrganizer(Long organizerId) {
        List<Long> eventIds = eventServiceClient.getEventIdsByOrganizer(organizerId);
        if (eventIds.isEmpty()) return List.of();
        return bookingRepository.findByEventIdIn(eventIds).stream()
                .map(b -> { EventDto event = fetchEventSafe(b.getEventId()); return toResponseDto(b, event); })
                .collect(Collectors.toList());
    }

    public List<BookingResponseDto> getAllBookings() {
        return bookingRepository.findAll().stream()
                .map(b -> { EventDto event = fetchEventSafe(b.getEventId()); return toResponseDto(b, event); })
                .collect(Collectors.toList());
    }

    public BookingResponseDto getBookingById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
        EventDto event = fetchEventSafe(booking.getEventId());
        return toResponseDto(booking, event);
    }

    private EventDto fetchEventSafe(Long eventId) {
        try { return eventServiceClient.getEventById(eventId); }
        catch (Exception ex) { return null; }
    }

    private UserDto fetchUserSafe(Long userId) {
        try { return userServiceClient.getUserById(userId); }
        catch (Exception ex) { return null; }
    }

    @Transactional
    public BookingResponseDto cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.EXPIRED) {
            return toResponseDto(booking, fetchEventSafe(booking.getEventId()));
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel an already confirmed booking.");
        }

        EventDto event = eventServiceClient.updateSeats(booking.getEventId(), booking.getQuantity());
        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.save(booking);
        return toResponseDto(saved, event);
    }

    /**
     * Processes a refund for a confirmed booking.
     * Rules:
     *  1. Booking must be CONFIRMED.
     *  2. Requested within 6 hours of confirmedAt.
     *  3. Event must be more than 24 hours away.
     * On success:
     *  - Seats restored in event-service.
     *  - Booking marked CANCELLED and refundedAt stamped.
     *  - Dedicated Refund record created in MySQL `refunds` table.
     *  - Payment marked REFUNDED in payment-service.
     */
    @Transactional
    public BookingResponseDto requestRefund(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Refund is only available for confirmed bookings. Current status: " + booking.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime confirmedAt = booking.getConfirmedAt();
        if (confirmedAt == null || now.isAfter(confirmedAt.plusHours(6))) {
            throw new IllegalStateException(
                    "REFUND_WINDOW_CLOSED: Refunds are only available within 6 hours of booking confirmation. " +
                    "Your refund window has expired.");
        }

        EventDto event = eventServiceClient.getEventById(booking.getEventId());
        if (event != null && event.getEventDate() != null
                && now.isAfter(event.getEventDate().minusHours(24))) {
            throw new IllegalStateException(
                    "EVENT_TOO_CLOSE: Refund option closes 24 hours before the event to prevent " +
                    "last-minute cancellations. The event is less than 24 hours away.");
        }

        // Restore seats
        eventServiceClient.updateSeats(booking.getEventId(), booking.getQuantity());

        // Cancel booking
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setRefundedAt(now);
        Booking saved = bookingRepository.save(booking);

        // Fetch user details to persist in dedicated refunds table
        UserDto user = fetchUserSafe(booking.getUserId());

        // Save entry into dedicated `refunds` MySQL table
        Refund refund = new Refund();
        refund.setBookingId(booking.getId());
        refund.setUserId(booking.getUserId());
        refund.setUserName(user != null ? user.getName() : "User #" + booking.getUserId());
        refund.setUserEmail(user != null ? user.getEmail() : "Unknown");
        refund.setEventId(booking.getEventId());
        refund.setEventName(event != null ? event.getName() : "Event #" + booking.getEventId());
        refund.setAmount(booking.getTotalAmount());
        refund.setRefundDate(now);
        refundRepository.save(refund);

        // Call payment-service to mark payment REFUNDED
        try {
            paymentServiceClient.refundPayment(bookingId);
        } catch (Exception ex) {
            System.err.println("[BookingService] Warning: could not mark payment as refunded for booking "
                    + bookingId + ": " + ex.getMessage());
        }

        return toResponseDto(saved, fetchEventSafe(saved.getEventId()));
    }

    /**
     * Admin view: retrieves all records directly from the dedicated `refunds` MySQL table.
     */
    public List<RefundSummaryDto> getRefundedBookings() {
        return refundRepository.findAllByOrderByRefundDateDesc()
                .stream()
                .map(r -> {
                    RefundSummaryDto dto = new RefundSummaryDto();
                    dto.setBookingId(r.getBookingId());
                    dto.setEventId(r.getEventId());
                    dto.setEventName(r.getEventName());
                    dto.setUserId(r.getUserId());
                    dto.setUserName(r.getUserName());
                    dto.setUserEmail(r.getUserEmail());
                    dto.setTotalAmount(r.getAmount());
                    dto.setRefundedAt(r.getRefundDate());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private BookingResponseDto toResponseDto(Booking booking, EventDto event) {
        BookingResponseDto dto = new BookingResponseDto();
        dto.setId(booking.getId());
        dto.setUserId(booking.getUserId());
        dto.setEventId(booking.getEventId());
        dto.setEventName(event != null ? event.getName() : "Event #" + booking.getEventId());
        dto.setEventVenue(event != null ? event.getVenue() : "");
        dto.setEventCity(event != null ? event.getCity() : "");
        dto.setEventDate(event != null ? event.getEventDate() : null);
        dto.setQuantity(booking.getQuantity());
        dto.setTotalAmount(booking.getTotalAmount());
        dto.setBookingDate(booking.getBookingDate());
        dto.setExpiresAt(booking.getExpiresAt());
        dto.setConfirmedAt(booking.getConfirmedAt());
        dto.setRefundedAt(booking.getRefundedAt());
        dto.setStatus(booking.getStatus());
        return dto;
    }
}