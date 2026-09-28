package com.eventticketplatform.bookingservice.service;

import com.eventticketplatform.bookingservice.client.EventServiceClient;
import com.eventticketplatform.bookingservice.client.PaymentServiceClient;
import com.eventticketplatform.bookingservice.client.UserServiceClient;
import com.eventticketplatform.bookingservice.dto.BookingRequestDto;
import com.eventticketplatform.bookingservice.dto.BookingResponseDto;
import com.eventticketplatform.bookingservice.dto.EventDto;
import com.eventticketplatform.bookingservice.dto.RefundSummaryDto;
import com.eventticketplatform.bookingservice.dto.UserDto;
import com.eventticketplatform.bookingservice.entity.Booking;
import com.eventticketplatform.bookingservice.entity.BookingStatus;
import com.eventticketplatform.bookingservice.entity.Refund;
import com.eventticketplatform.bookingservice.exception.ResourceNotFoundException;
import com.eventticketplatform.bookingservice.repository.BookingRepository;
import com.eventticketplatform.bookingservice.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private EventServiceClient eventServiceClient;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    @InjectMocks
    private BookingService bookingService;

    private UserDto attendeeUser;
    private UserDto organizerUser;
    private EventDto sampleEvent;
    private Booking sampleBooking;

    @BeforeEach
    void setUp() {
        attendeeUser = new UserDto();
        attendeeUser.setId(1L);
        attendeeUser.setName("John Doe");
        attendeeUser.setEmail("john@example.com");
        attendeeUser.setRole("USER");

        organizerUser = new UserDto();
        organizerUser.setId(2L);
        organizerUser.setName("Alice Organizer");
        organizerUser.setEmail("alice@example.com");
        organizerUser.setRole("ORGANIZER");

        sampleEvent = new EventDto();
        sampleEvent.setId(10L);
        sampleEvent.setName("Tech Conference 2026");
        sampleEvent.setVenue("Grand Hall");
        sampleEvent.setCity("New York");
        sampleEvent.setEventDate(LocalDateTime.now().plusDays(5));
        sampleEvent.setTotalSeats(100);
        sampleEvent.setAvailableSeats(90);
        sampleEvent.setPrice(new BigDecimal("50.00"));

        sampleBooking = new Booking();
        sampleBooking.setId(100L);
        sampleBooking.setUserId(1L);
        sampleBooking.setEventId(10L);
        sampleBooking.setQuantity(2);
        sampleBooking.setTotalAmount(new BigDecimal("100.00"));
        sampleBooking.setBookingDate(LocalDateTime.now());
        sampleBooking.setStatus(BookingStatus.PENDING_PAYMENT);
        sampleBooking.setExpiresAt(LocalDateTime.now().plusMinutes(5));
    }

    // ==========================================
    // 1. Tests for createBooking()
    // ==========================================

    @Test
    @DisplayName("POSITIVE: createBooking holds seats and returns pending booking response")
    void createBooking_Positive_Success() {
        BookingRequestDto requestDto = new BookingRequestDto();
        requestDto.setUserId(1L);
        requestDto.setEventId(10L);
        requestDto.setQuantity(2);

        when(userServiceClient.getUserById(1L)).thenReturn(attendeeUser);
        when(eventServiceClient.updateSeats(10L, -2)).thenReturn(sampleEvent);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(100L);
            return b;
        });

        BookingResponseDto response = bookingService.createBooking(requestDto);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(1L, response.getUserId());
        assertEquals(10L, response.getEventId());
        assertEquals(2, response.getQuantity());
        assertEquals(new BigDecimal("100.00"), response.getTotalAmount());
        assertEquals(BookingStatus.PENDING_PAYMENT, response.getStatus());

        verify(eventServiceClient).updateSeats(10L, -2);
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    @DisplayName("NEGATIVE: createBooking throws IllegalArgumentException when user is an ORGANIZER")
    void createBooking_Negative_OrganizerNotAllowed() {
        BookingRequestDto requestDto = new BookingRequestDto();
        requestDto.setUserId(2L);
        requestDto.setEventId(10L);
        requestDto.setQuantity(1);

        when(userServiceClient.getUserById(2L)).thenReturn(organizerUser);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            bookingService.createBooking(requestDto);
        });

        assertTrue(exception.getMessage().contains("Organizers cannot book tickets"));
        verify(eventServiceClient, never()).updateSeats(anyLong(), anyInt());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("NEGATIVE: createBooking throws IllegalArgumentException when quantity is invalid (<= 0)")
    void createBooking_Negative_InvalidQuantity() {
        BookingRequestDto requestDto = new BookingRequestDto();
        requestDto.setUserId(1L);
        requestDto.setEventId(10L);
        requestDto.setQuantity(0);

        when(userServiceClient.getUserById(1L)).thenReturn(attendeeUser);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            bookingService.createBooking(requestDto);
        });

        assertTrue(exception.getMessage().contains("quantity must be at least 1"));
        verify(eventServiceClient, never()).updateSeats(anyLong(), anyInt());
    }

    @Test
    @DisplayName("NEGATIVE: createBooking rollbacks reserved seats if saving booking fails")
    void createBooking_Negative_RollsBackSeatsOnFailure() {
        BookingRequestDto requestDto = new BookingRequestDto();
        requestDto.setUserId(1L);
        requestDto.setEventId(10L);
        requestDto.setQuantity(2);

        when(userServiceClient.getUserById(1L)).thenReturn(attendeeUser);
        when(eventServiceClient.updateSeats(10L, -2)).thenReturn(sampleEvent);
        when(bookingRepository.save(any(Booking.class))).thenThrow(new RuntimeException("Database error"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            bookingService.createBooking(requestDto);
        });

        assertTrue(exception.getMessage().contains("Failed to create booking hold"));
        verify(eventServiceClient).updateSeats(10L, 2);
    }

    // ==========================================
    // 2. Tests for confirmBooking()
    // ==========================================

    @Test
    @DisplayName("POSITIVE: confirmBooking changes status to CONFIRMED when booking is valid")
    void confirmBooking_Positive_Success() {
        sampleBooking.setStatus(BookingStatus.PENDING_PAYMENT);
        sampleBooking.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(sampleBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(eventServiceClient.getEventById(10L)).thenReturn(sampleEvent);

        BookingResponseDto response = bookingService.confirmBooking(100L);

        assertNotNull(response);
        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        assertNotNull(sampleBooking.getConfirmedAt());
        verify(bookingRepository).save(sampleBooking);
    }

    @Test
    @DisplayName("POSITIVE: confirmBooking is idempotent and returns immediately if already CONFIRMED")
    void confirmBooking_Positive_AlreadyConfirmed() {
        sampleBooking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(sampleBooking));
        when(eventServiceClient.getEventById(10L)).thenReturn(sampleEvent);

        BookingResponseDto response = bookingService.confirmBooking(100L);

        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("NEGATIVE: confirmBooking throws ResourceNotFoundException when booking ID is missing")
    void confirmBooking_Negative_NotFound() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            bookingService.confirmBooking(999L);
        });
    }

    @Test
    @DisplayName("NEGATIVE: confirmBooking throws IllegalStateException if status is CANCELLED or EXPIRED")
    void confirmBooking_Negative_AlreadyCancelledOrExpired() {
        sampleBooking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(sampleBooking));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            bookingService.confirmBooking(100L);
        });
        assertTrue(exception.getMessage().contains("Cannot confirm booking"));
    }

    @Test
    @DisplayName("NEGATIVE: confirmBooking marks EXPIRED and releases seats if hold time has passed")
    void confirmBooking_Negative_ExpiredHold() {
        sampleBooking.setStatus(BookingStatus.PENDING_PAYMENT);
        sampleBooking.setExpiresAt(LocalDateTime.now().minusMinutes(2));

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(sampleBooking));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            bookingService.confirmBooking(100L);
        });

        assertTrue(exception.getMessage().contains("reservation has expired"));
        assertEquals(BookingStatus.EXPIRED, sampleBooking.getStatus());
        verify(eventServiceClient).updateSeats(10L, 2);
        verify(bookingRepository).save(sampleBooking);
    }

    // ==========================================
    // 3. Tests for cancelBooking()
    // ==========================================

    @Test
    @DisplayName("POSITIVE: cancelBooking releases seats and changes status to CANCELLED for pending booking")
    void cancelBooking_Positive_Success() {
        sampleBooking.setStatus(BookingStatus.PENDING_PAYMENT);

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(sampleBooking));
        when(eventServiceClient.updateSeats(10L, 2)).thenReturn(sampleEvent);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponseDto response = bookingService.cancelBooking(100L);

        assertEquals(BookingStatus.CANCELLED, response.getStatus());
        verify(eventServiceClient).updateSeats(10L, 2);
        verify(bookingRepository).save(sampleBooking);
    }

    @Test
    @DisplayName("POSITIVE: cancelBooking returns existing state if already CANCELLED")
    void cancelBooking_Positive_AlreadyCancelled() {
        sampleBooking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(sampleBooking));
        when(eventServiceClient.getEventById(10L)).thenReturn(sampleEvent);

        BookingResponseDto response = bookingService.cancelBooking(100L);

        assertEquals(BookingStatus.CANCELLED, response.getStatus());
        verify(eventServiceClient, never()).updateSeats(anyLong(), anyInt());
    }

    @Test
    @DisplayName("NEGATIVE: cancelBooking throws IllegalStateException if booking is already CONFIRMED")
    void cancelBooking_Negative_ConfirmedBookingCannotBeCancelledDirectly() {
        sampleBooking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(sampleBooking));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            bookingService.cancelBooking(100L);
        });
        assertTrue(exception.getMessage().contains("Cannot cancel an already confirmed booking"));
    }

    // ==========================================
    // 4. Tests for requestRefund()
    // ==========================================

    @Test
    @DisplayName("POSITIVE: requestRefund processes refund for confirmed booking within limits")
    void requestRefund_Positive_Success() {
        sampleBooking.setStatus(BookingStatus.CONFIRMED);
        sampleBooking.setConfirmedAt(LocalDateTime.now().minusHours(1));

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(sampleBooking));
        when(eventServiceClient.getEventById(10L)).thenReturn(sampleEvent);
        when(eventServiceClient.updateSeats(10L, 2)).thenReturn(sampleEvent);
        when(userServiceClient.getUserById(1L)).thenReturn(attendeeUser);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponseDto response = bookingService.requestRefund(100L);

        assertEquals(BookingStatus.CANCELLED, response.getStatus());
        assertNotNull(sampleBooking.getRefundedAt());
        verify(eventServiceClient).updateSeats(10L, 2);
        verify(refundRepository).save(any(Refund.class));
        verify(paymentServiceClient).refundPayment(100L);
    }

    @Test
    @DisplayName("NEGATIVE: requestRefund throws IllegalStateException if booking is not CONFIRMED")
    void requestRefund_Negative_NotConfirmed() {
        sampleBooking.setStatus(BookingStatus.PENDING_PAYMENT);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(sampleBooking));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            bookingService.requestRefund(100L);
        });
        assertTrue(exception.getMessage().contains("Refund is only available for confirmed bookings"));
    }

    @Test
    @DisplayName("NEGATIVE: requestRefund throws IllegalStateException if confirmed > 6 hours ago")
    void requestRefund_Negative_WindowClosed() {
        sampleBooking.setStatus(BookingStatus.CONFIRMED);
        sampleBooking.setConfirmedAt(LocalDateTime.now().minusHours(7));

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(sampleBooking));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            bookingService.requestRefund(100L);
        });
        assertTrue(exception.getMessage().contains("REFUND_WINDOW_CLOSED"));
    }

    @Test
    @DisplayName("NEGATIVE: requestRefund throws IllegalStateException if event is less than 24 hours away")
    void requestRefund_Negative_EventTooClose() {
        sampleBooking.setStatus(BookingStatus.CONFIRMED);
        sampleBooking.setConfirmedAt(LocalDateTime.now().minusHours(1));
        sampleEvent.setEventDate(LocalDateTime.now().plusHours(10));

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(sampleBooking));
        when(eventServiceClient.getEventById(10L)).thenReturn(sampleEvent);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            bookingService.requestRefund(100L);
        });
        assertTrue(exception.getMessage().contains("EVENT_TOO_CLOSE"));
    }

    // ==========================================
    // 5. Tests for Query Methods
    // ==========================================

    @Test
    @DisplayName("POSITIVE: getBookingById returns booking when found")
    void getBookingById_Positive_Success() {
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(sampleBooking));
        when(eventServiceClient.getEventById(10L)).thenReturn(sampleEvent);

        BookingResponseDto result = bookingService.getBookingById(100L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("Tech Conference 2026", result.getEventName());
    }

    @Test
    @DisplayName("NEGATIVE: getBookingById throws ResourceNotFoundException when missing")
    void getBookingById_Negative_NotFound() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            bookingService.getBookingById(999L);
        });
    }

    @Test
    @DisplayName("POSITIVE: getBookingsByUser returns user's booking list")
    void getBookingsByUser_Positive_ReturnsList() {
        when(bookingRepository.findByUserId(1L)).thenReturn(List.of(sampleBooking));
        when(eventServiceClient.getEventById(10L)).thenReturn(sampleEvent);

        List<BookingResponseDto> list = bookingService.getBookingsByUser(1L);

        assertEquals(1, list.size());
        assertEquals(100L, list.get(0).getId());
    }

    @Test
    @DisplayName("POSITIVE: getBookingsByOrganizer returns empty list when organizer has no events")
    void getBookingsByOrganizer_Positive_EmptyEvents() {
        when(eventServiceClient.getEventIdsByOrganizer(2L)).thenReturn(List.of());

        List<BookingResponseDto> result = bookingService.getBookingsByOrganizer(2L);

        assertTrue(result.isEmpty());
        verify(bookingRepository, never()).findByEventIdIn(any());
    }

    @Test
    @DisplayName("POSITIVE: getRefundedBookings returns list from refundRepository")
    void getRefundedBookings_Positive_Success() {
        Refund refund = new Refund();
        refund.setId(10L);
        refund.setBookingId(100L);
        refund.setEventId(10L);
        refund.setEventName("Tech Conference 2026");
        refund.setUserId(1L);
        refund.setUserName("John Doe");
        refund.setUserEmail("john@example.com");
        refund.setAmount(new BigDecimal("100.00"));
        refund.setRefundDate(LocalDateTime.now());

        when(refundRepository.findAllByOrderByRefundDateDesc()).thenReturn(List.of(refund));

        List<RefundSummaryDto> refunds = bookingService.getRefundedBookings();

        assertEquals(1, refunds.size());
        assertEquals("Tech Conference 2026", refunds.get(0).getEventName());
        assertEquals("John Doe", refunds.get(0).getUserName());
    }
}
