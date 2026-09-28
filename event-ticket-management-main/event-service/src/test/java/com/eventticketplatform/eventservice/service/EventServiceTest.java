package com.eventticketplatform.eventservice.service;

import com.eventticketplatform.eventservice.dto.EventDto;
import com.eventticketplatform.eventservice.entity.Event;
import com.eventticketplatform.eventservice.exception.ResourceNotFoundException;
import com.eventticketplatform.eventservice.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    private Event event;

    @BeforeEach
    void setUp() {
        event = new Event();
        event.setId(1L);
        event.setName("Coldplay Live");
        event.setVenue("Wembley");
        event.setCity("London");
        event.setEventDate(LocalDateTime.now().plusDays(10));
        event.setTotalSeats(100);
        event.setAvailableSeats(100);
        event.setPrice(new BigDecimal("120.00"));
        event.setCategory("Music");
        event.setOrganizerId(5L);
    }

    // ==========================================
    // 1. Tests for getEventById()
    // ==========================================
    @Nested
    @DisplayName("getEventById() Tests")
    class GetEventByIdTests {

        @Test
        @DisplayName("POSITIVE: returns EventDto when event is found")
        void getEventById_returnsEvent_whenFound() {
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            EventDto result = eventService.getEventById(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("Coldplay Live", result.getName());
            assertEquals("London", result.getCity());
        }

        @Test
        @DisplayName("NEGATIVE: throws ResourceNotFoundException when event ID is not found")
        void getEventById_throwsException_whenNotFound() {
            when(eventRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> eventService.getEventById(99L));
        }
    }


    @Nested
    @DisplayName("Query Methods Tests")
    class QueryMethodsTests {

        @Test
        @DisplayName("POSITIVE: getAllEvents returns paged EventDto list")
        void getAllEvents_returnsPagedEvents() {
            Page<Event> page = new PageImpl<>(List.of(event));
            when(eventRepository.findByFilters(eq("Coldplay"), eq("London"), eq("Music"), eq(5L), any(Pageable.class)))
                    .thenReturn(page);

            Page<EventDto> result = eventService.getAllEvents("Coldplay", "London", "Music", 5L, 0, 10);

            assertEquals(1, result.getTotalElements());
            assertEquals("Coldplay Live", result.getContent().get(0).getName());
        }

        @Test
        @DisplayName("POSITIVE: getEventIdsByOrganizer returns list of event IDs for given organizer")
        void getEventIdsByOrganizer_returnsIdList() {
            when(eventRepository.findIdsByOrganizerId(5L)).thenReturn(List.of(1L, 2L, 3L));

            List<Long> ids = eventService.getEventIdsByOrganizer(5L);

            assertEquals(3, ids.size());
            assertTrue(ids.contains(1L));
        }
    }

    @Nested
    @DisplayName("Create & Update Event Tests")
    class CreateUpdateEventTests {

        @Test
        @DisplayName("POSITIVE: createEvent sets availableSeats equal to totalSeats and saves entity")
        void createEvent_setsAvailableSeatsToTotalSeats() {
            EventDto dto = new EventDto();
            dto.setName("New Show");
            dto.setTotalSeats(50);
            dto.setPrice(new BigDecimal("30.00"));

            when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

            EventDto result = eventService.createEvent(dto);

            assertEquals("New Show", result.getName());
            assertEquals(50, result.getAvailableSeats());
        }

        @Test
        @DisplayName("POSITIVE: updateEvent updates event fields and saves")
        void updateEvent_Positive_Success() {
            EventDto updateDto = new EventDto();
            updateDto.setName("Coldplay Live - Night 2");
            updateDto.setVenue("Wembley Stadium");
            updateDto.setCity("London");
            updateDto.setTotalSeats(120);
            updateDto.setAvailableSeats(120);
            updateDto.setPrice(new BigDecimal("150.00"));

            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
            when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

            EventDto result = eventService.updateEvent(1L, updateDto);

            assertEquals("Coldplay Live - Night 2", result.getName());
            assertEquals(120, result.getTotalSeats());
            assertEquals(new BigDecimal("150.00"), result.getPrice());
        }

        @Test
        @DisplayName("NEGATIVE: updateEvent throws ResourceNotFoundException when event ID does not exist")
        void updateEvent_Negative_NotFound() {
            EventDto updateDto = new EventDto();
            when(eventRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> eventService.updateEvent(99L, updateDto));
        }
    }

    // ==========================================
    // 4. Tests for deleteEvent()
    // ==========================================
    @Nested
    @DisplayName("deleteEvent() Tests")
    class DeleteEventTests {

        @Test
        @DisplayName("POSITIVE: deleteEvent deletes successfully when no seats have been booked")
        void deleteEvent_deletesSuccessfully_whenNoSeatsBooked() {
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            eventService.deleteEvent(1L);

            verify(eventRepository).deleteById(1L);
        }

        @Test
        @DisplayName("NEGATIVE: deleteEvent throws IllegalArgumentException when seats are already booked")
        void deleteEvent_throwsException_whenSeatsAlreadyBooked() {
            event.setAvailableSeats(60); // 40 seats booked

            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            assertThrows(IllegalArgumentException.class, () -> eventService.deleteEvent(1L));
            verify(eventRepository, never()).deleteById(any());
        }
    }

    // ==========================================
    // 5. Tests for updateSeats()
    // ==========================================
    @Nested
    @DisplayName("updateSeats() Tests")
    class UpdateSeatsTests {

        @Test
        @DisplayName("POSITIVE: updateSeats deducts seats when seatsChange is negative and seats are available")
        void updateSeats_deductsSeats_whenAvailable() {
            when(eventRepository.deductSeatsAtomic(1L, 5)).thenReturn(1);
            event.setAvailableSeats(95);
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            EventDto result = eventService.updateSeats(1L, -5);

            assertEquals(95, result.getAvailableSeats());
        }

        @Test
        @DisplayName("POSITIVE: updateSeats restores seats when seatsChange is positive")
        void updateSeats_restoresSeats_successfully() {
            when(eventRepository.restoreSeatsAtomic(1L, 5)).thenReturn(1);
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            EventDto result = eventService.updateSeats(1L, 5);

            assertEquals(100, result.getAvailableSeats());
        }

        @Test
        @DisplayName("POSITIVE: updateSeats with 0 change returns current state without db atomic call")
        void updateSeats_zeroChange_returnsCurrentState() {
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            EventDto result = eventService.updateSeats(1L, 0);

            assertEquals(100, result.getAvailableSeats());
            verify(eventRepository, never()).deductSeatsAtomic(anyLong(), anyInt());
            verify(eventRepository, never()).restoreSeatsAtomic(anyLong(), anyInt());
        }

        @Test
        @DisplayName("NEGATIVE: updateSeats throws IllegalStateException when event is sold out")
        void updateSeats_throwsException_whenSoldOut() {
            when(eventRepository.deductSeatsAtomic(1L, 5)).thenReturn(0);
            event.setAvailableSeats(0);
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> eventService.updateSeats(1L, -5));
            assertTrue(ex.getMessage().contains("SOLD_OUT"));
        }

        @Test
        @DisplayName("NEGATIVE: updateSeats throws IllegalStateException when not enough seats remaining")
        void updateSeats_throwsException_whenNotEnoughSeats() {
            when(eventRepository.deductSeatsAtomic(1L, 5)).thenReturn(0);
            event.setAvailableSeats(2);
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> eventService.updateSeats(1L, -5));
            assertTrue(ex.getMessage().contains("NOT_ENOUGH_SEATS"));
        }

        @Test
        @DisplayName("NEGATIVE: updateSeats throws IllegalArgumentException when trying to restore more seats than total capacity")
        void updateSeats_throwsException_whenRestoreExceedsCapacity() {
            when(eventRepository.restoreSeatsAtomic(1L, 10)).thenReturn(0);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> eventService.updateSeats(1L, 10));
            assertTrue(ex.getMessage().contains("Cannot restore more seats"));
        }
    }
}