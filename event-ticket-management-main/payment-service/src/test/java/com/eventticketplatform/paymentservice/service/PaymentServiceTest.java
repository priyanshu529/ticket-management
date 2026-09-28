package com.eventticketplatform.paymentservice.service;

import com.eventticketplatform.paymentservice.client.BookingServiceClient;
import com.eventticketplatform.paymentservice.dto.BookingDto;
import com.eventticketplatform.paymentservice.dto.CreateOrderRequestDto;
import com.eventticketplatform.paymentservice.dto.PaymentResponseDto;
import com.eventticketplatform.paymentservice.dto.VerifyPaymentRequestDto;
import com.eventticketplatform.paymentservice.entity.BookingStatus;
import com.eventticketplatform.paymentservice.entity.Payment;
import com.eventticketplatform.paymentservice.entity.PaymentStatus;
import com.eventticketplatform.paymentservice.repository.PaymentRepository;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingServiceClient bookingServiceClient;

    @InjectMocks
    private PaymentService paymentService;

    private String testKeyId = "rzp_test_123456";
    private String testKeySecret = "test_secret_key_123456";

    private BookingDto sampleBooking;
    private Payment samplePayment;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", testKeyId);
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", testKeySecret);

        sampleBooking = new BookingDto();
        sampleBooking.setId(100L);
        sampleBooking.setUserId(1L);
        sampleBooking.setEventId(10L);
        sampleBooking.setQuantity(2);
        sampleBooking.setTotalAmount(new BigDecimal("150.00"));
        sampleBooking.setStatus(BookingStatus.PENDING_PAYMENT);

        samplePayment = new Payment();
        samplePayment.setId(50L);
        samplePayment.setBookingId(100L);
        samplePayment.setUserId(1L);
        samplePayment.setAmount(new BigDecimal("150.00"));
        samplePayment.setPaymentMethod("RAZORPAY");
        samplePayment.setStatus(PaymentStatus.PENDING);
        samplePayment.setRazorpayOrderId("order_N123456");
    }

    private String calculateHmacSha256(String data, String secret) {
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secret).hmacHex(data);
    }

    // ==========================================
    // 1. Tests for createOrder()
    // ==========================================

    @Test
    @DisplayName("NEGATIVE: createOrder throws IllegalArgumentException when booking is CANCELLED")
    void createOrder_Negative_CancelledBooking() {
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setBookingId(100L);
        request.setUserId(1L);

        sampleBooking.setStatus(BookingStatus.CANCELLED);
        when(bookingServiceClient.getBookingById(100L)).thenReturn(sampleBooking);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            paymentService.createOrder(request);
        });
        assertTrue(exception.getMessage().contains("Cannot pay for a CANCELLED booking"));
    }

    @Test
    @DisplayName("NEGATIVE: createOrder throws IllegalArgumentException when booking is EXPIRED")
    void createOrder_Negative_ExpiredBooking() {
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setBookingId(100L);
        request.setUserId(1L);

        sampleBooking.setStatus(BookingStatus.EXPIRED);
        when(bookingServiceClient.getBookingById(100L)).thenReturn(sampleBooking);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            paymentService.createOrder(request);
        });
        assertTrue(exception.getMessage().contains("Cannot pay for a EXPIRED booking"));
    }

    @Test
    @DisplayName("NEGATIVE: createOrder throws IllegalArgumentException when user does not own booking")
    void createOrder_Negative_UserMismatch() {
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setBookingId(100L);
        request.setUserId(99L);

        when(bookingServiceClient.getBookingById(100L)).thenReturn(sampleBooking);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            paymentService.createOrder(request);
        });
        assertTrue(exception.getMessage().contains("User does not own this booking"));
    }

    @Test
    @DisplayName("NEGATIVE: createOrder throws IllegalArgumentException when booking is already paid")
    void createOrder_Negative_AlreadyPaid() {
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setBookingId(100L);
        request.setUserId(1L);

        Payment completedPayment = new Payment();
        completedPayment.setStatus(PaymentStatus.COMPLETED);

        when(bookingServiceClient.getBookingById(100L)).thenReturn(sampleBooking);
        when(paymentRepository.findByBookingId(100L)).thenReturn(Optional.of(completedPayment));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            paymentService.createOrder(request);
        });
        assertTrue(exception.getMessage().contains("Booking is already paid"));
    }

    @Test
    @DisplayName("NEGATIVE: createOrder throws IllegalArgumentException when no bookingId and amount is null/zero")
    void createOrder_Negative_MissingAmountNoBookingId() {
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setBookingId(null);
        request.setAmount(BigDecimal.ZERO);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            paymentService.createOrder(request);
        });
        assertTrue(exception.getMessage().contains("amount is required when bookingId is not supplied"));
    }

    @Test
    @DisplayName("NEGATIVE: createOrder wraps RazorpayException into IllegalStateException when API call fails")
    void createOrder_Negative_RazorpayApiError() {
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setBookingId(100L);
        request.setUserId(1L);

        when(bookingServiceClient.getBookingById(100L)).thenReturn(sampleBooking);
        when(paymentRepository.findByBookingId(100L)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            paymentService.createOrder(request);
        });
        assertTrue(exception.getMessage().contains("Failed to create Razorpay order"));
    }

    // ==========================================
    // 2. Tests for verifyPayment()
    // ==========================================

    @Test
    @DisplayName("POSITIVE: verifyPayment completes payment and confirms booking when signature is valid")
    void verifyPayment_Positive_Success() {
        String orderId = "order_N123456";
        String paymentId = "pay_P987654";
        String validSignature = calculateHmacSha256(orderId + "|" + paymentId, testKeySecret);

        VerifyPaymentRequestDto verifyRequest = new VerifyPaymentRequestDto();
        verifyRequest.setRazorpayOrderId(orderId);
        verifyRequest.setRazorpayPaymentId(paymentId);
        verifyRequest.setRazorpaySignature(validSignature);
        verifyRequest.setBookingId(100L);

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(samplePayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponseDto response = paymentService.verifyPayment(verifyRequest);

        assertNotNull(response);
        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertEquals(paymentId, response.getRazorpayPaymentId());

        verify(paymentRepository, times(1)).save(samplePayment);
        verify(bookingServiceClient).confirmBooking(100L);
    }

    @Test
    @DisplayName("NEGATIVE: verifyPayment marks payment FAILED and throws exception when signature is invalid")
    void verifyPayment_Negative_InvalidSignature() {
        String orderId = "order_N123456";
        String paymentId = "pay_P987654";
        String invalidSignature = "invalid_signature_xyz";

        VerifyPaymentRequestDto verifyRequest = new VerifyPaymentRequestDto();
        verifyRequest.setRazorpayOrderId(orderId);
        verifyRequest.setRazorpayPaymentId(paymentId);
        verifyRequest.setRazorpaySignature(invalidSignature);

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(samplePayment));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            paymentService.verifyPayment(verifyRequest);
        });

        assertTrue(exception.getMessage().contains("Payment signature verification failed"));
        assertEquals(PaymentStatus.FAILED, samplePayment.getStatus());
        verify(paymentRepository).save(samplePayment);
        verify(bookingServiceClient, never()).confirmBooking(anyLong());
    }

    @Test
    @DisplayName("NEGATIVE: verifyPayment throws IllegalArgumentException when razorpayOrderId is not found")
    void verifyPayment_Negative_OrderNotFound() {
        VerifyPaymentRequestDto verifyRequest = new VerifyPaymentRequestDto();
        verifyRequest.setRazorpayOrderId("order_NONEXISTENT");

        when(paymentRepository.findByRazorpayOrderId("order_NONEXISTENT")).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            paymentService.verifyPayment(verifyRequest);
        });
        assertTrue(exception.getMessage().contains("No payment found for Razorpay order"));
    }

    @Test
    @DisplayName("NEGATIVE: verifyPayment throws IllegalStateException if booking confirmation throws error")
    void verifyPayment_Negative_BookingConfirmationFails() {
        String orderId = "order_N123456";
        String paymentId = "pay_P987654";
        String validSignature = calculateHmacSha256(orderId + "|" + paymentId, testKeySecret);

        VerifyPaymentRequestDto verifyRequest = new VerifyPaymentRequestDto();
        verifyRequest.setRazorpayOrderId(orderId);
        verifyRequest.setRazorpayPaymentId(paymentId);
        verifyRequest.setRazorpaySignature(validSignature);
        verifyRequest.setBookingId(100L);

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(samplePayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("Booking expired in booking-service"))
                .when(bookingServiceClient).confirmBooking(100L);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            paymentService.verifyPayment(verifyRequest);
        });
        assertTrue(exception.getMessage().contains("Payment was successful but booking confirmation failed"));
    }

    // ==========================================
    // 3. Tests for refundPayment()
    // ==========================================

    @Test
    @DisplayName("POSITIVE: refundPayment marks COMPLETED payment as REFUNDED")
    void refundPayment_Positive_Success() {
        samplePayment.setStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findByBookingId(100L)).thenReturn(Optional.of(samplePayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponseDto response = paymentService.refundPayment(100L);

        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
        assertNotNull(response.getRefundDate());
        verify(paymentRepository).save(samplePayment);
    }

    @Test
    @DisplayName("POSITIVE: refundPayment is idempotent if payment is already REFUNDED")
    void refundPayment_Positive_AlreadyRefunded() {
        samplePayment.setStatus(PaymentStatus.REFUNDED);
        samplePayment.setRefundDate(LocalDateTime.now().minusDays(1));
        when(paymentRepository.findByBookingId(100L)).thenReturn(Optional.of(samplePayment));

        PaymentResponseDto response = paymentService.refundPayment(100L);

        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("POSITIVE: refundPayment returns empty DTO gracefully if no payment record exists")
    void refundPayment_Positive_NullPaymentRecord() {
        when(paymentRepository.findByBookingId(999L)).thenReturn(Optional.empty());

        PaymentResponseDto response = paymentService.refundPayment(999L);

        assertNotNull(response);
        assertNull(response.getId());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("NEGATIVE: refundPayment throws IllegalStateException if payment status is PENDING or FAILED")
    void refundPayment_Negative_NotCompleted() {
        samplePayment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByBookingId(100L)).thenReturn(Optional.of(samplePayment));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            paymentService.refundPayment(100L);
        });
        assertTrue(exception.getMessage().contains("Cannot refund a payment in status: PENDING"));
    }

    // ==========================================
    // 4. Tests for getPaymentByBooking()
    // ==========================================

    @Test
    @DisplayName("POSITIVE: getPaymentByBooking returns payment response when found")
    void getPaymentByBooking_Positive_Success() {
        when(paymentRepository.findByBookingId(100L)).thenReturn(Optional.of(samplePayment));

        PaymentResponseDto response = paymentService.getPaymentByBooking(100L);

        assertNotNull(response);
        assertEquals(50L, response.getId());
        assertEquals(100L, response.getBookingId());
    }

    @Test
    @DisplayName("NEGATIVE: getPaymentByBooking throws RuntimeException when missing")
    void getPaymentByBooking_Negative_NotFound() {
        when(paymentRepository.findByBookingId(999L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            paymentService.getPaymentByBooking(999L);
        });
        assertTrue(exception.getMessage().contains("No payment found for booking"));
    }
}
