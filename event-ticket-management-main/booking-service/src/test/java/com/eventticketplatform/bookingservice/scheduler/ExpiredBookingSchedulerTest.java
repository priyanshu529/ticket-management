package com.eventticketplatform.bookingservice.scheduler;

import com.eventticketplatform.bookingservice.client.EventServiceClient;
import com.eventticketplatform.bookingservice.entity.Booking;
import com.eventticketplatform.bookingservice.entity.BookingStatus;
import com.eventticketplatform.bookingservice.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExpiredBookingSchedulerTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private EventServiceClient eventServiceClient;

    @InjectMocks
    private ExpiredBookingScheduler expiredBookingScheduler;

    private Booking expiredBooking1;
    private Booking expiredBooking2;

    @BeforeEach
    void setUp() {
        expiredBooking1 = new Booking();
        expiredBooking1.setId(101L);
        expiredBooking1.setEventId(10L);
        expiredBooking1.setQuantity(2);
        expiredBooking1.setStatus(BookingStatus.PENDING_PAYMENT);
        expiredBooking1.setExpiresAt(LocalDateTime.now().minusMinutes(5));

        expiredBooking2 = new Booking();
        expiredBooking2.setId(102L);
        expiredBooking2.setEventId(20L);
        expiredBooking2.setQuantity(3);
        expiredBooking2.setStatus(BookingStatus.PENDING_PAYMENT);
        expiredBooking2.setExpiresAt(LocalDateTime.now().minusMinutes(10));
    }

    @Test
    @DisplayName("POSITIVE: cleanupExpiredReservations marks expired pending bookings as EXPIRED and releases seats")
    void cleanupExpiredReservations_Positive_Success() {
        // ARRANGE
        when(bookingRepository.findByStatusAndExpiresAtBefore(eq(BookingStatus.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredBooking1, expiredBooking2));

        // ACT
        expiredBookingScheduler.cleanupExpiredReservations();

        // ASSERT
        assertEquals(BookingStatus.EXPIRED, expiredBooking1.getStatus());
        assertEquals(BookingStatus.EXPIRED, expiredBooking2.getStatus());

        verify(bookingRepository).save(expiredBooking1);
        verify(bookingRepository).save(expiredBooking2);
        verify(eventServiceClient).updateSeats(10L, 2);
        verify(eventServiceClient).updateSeats(20L, 3);
    }

    @Test
    @DisplayName("POSITIVE: cleanupExpiredReservations does nothing when no expired bookings exist")
    void cleanupExpiredReservations_Positive_NoExpiredBookings() {
        // ARRANGE
        when(bookingRepository.findByStatusAndExpiresAtBefore(eq(BookingStatus.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // ACT
        expiredBookingScheduler.cleanupExpiredReservations();

        // ASSERT
        verify(bookingRepository, never()).save(any());
        verify(eventServiceClient, never()).updateSeats(anyLong(), anyInt());
    }

    @Test
    @DisplayName("NEGATIVE: cleanupExpiredReservations handles exceptions gracefully per booking without crashing")
    void cleanupExpiredReservations_Negative_HandlesExceptionPerBooking() {
        // ARRANGE
        when(bookingRepository.findByStatusAndExpiresAtBefore(eq(BookingStatus.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredBooking1, expiredBooking2));

        // Throw error on restoring seats for booking 1
        doThrow(new RuntimeException("Feign communication failure"))
                .when(eventServiceClient).updateSeats(10L, 2);

        // ACT
        expiredBookingScheduler.cleanupExpiredReservations();

        // ASSERT - verify booking 2 is still processed despite booking 1 failure
        assertEquals(BookingStatus.EXPIRED, expiredBooking1.getStatus());
        assertEquals(BookingStatus.EXPIRED, expiredBooking2.getStatus());
        verify(eventServiceClient).updateSeats(20L, 3);
    }
}
