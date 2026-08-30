package com.wrx.booking.service;

import com.wrx.booking.domain.BookingSuccessEvent;
import com.wrx.booking.repository.BookingEventRepository;
import com.wrx.booking.repository.ConsumeLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingEventHandlerTest {

    private static final String GROUP = "booking-success-consumer";

    @Mock
    private ConsumeLogRepository consumeLogRepository;

    @Mock
    private BookingEventRepository bookingEventRepository;

    @Test
    void handlesEventAndWritesProjectionAndAudit() {
        when(consumeLogRepository.insertSuccess("booking:1:reserved", GROUP)).thenReturn(1);
        BookingEventHandler handler = new BookingEventHandler(consumeLogRepository, bookingEventRepository, false, "none");

        handler.handle(event(), GROUP);

        verify(bookingEventRepository).upsertProjection(event(), "RESERVED");
        verify(bookingEventRepository).insertAudit(event(), GROUP);
    }

    @Test
    void skipsDuplicateBeforeBusinessProcessing() {
        when(consumeLogRepository.insertSuccess("booking:1:reserved", GROUP)).thenReturn(0);
        BookingEventHandler handler = new BookingEventHandler(consumeLogRepository, bookingEventRepository, false, "none");

        handler.handle(event(), GROUP);

        verifyNoInteractions(bookingEventRepository);
    }

    @Test
    void injectedProjectionFailureIsPropagatedForKafkaRetry() {
        when(consumeLogRepository.insertSuccess("booking:1:reserved", GROUP)).thenReturn(1);
        BookingEventHandler handler = new BookingEventHandler(
                consumeLogRepository, bookingEventRepository, true, "projection-update"
        );

        assertThrows(IllegalStateException.class, () -> handler.handle(event(), GROUP));
        verify(bookingEventRepository, never()).upsertProjection(any(), anyString());
    }

    private BookingSuccessEvent event() {
        return new BookingSuccessEvent(
                "booking:1:reserved", 1L, 86053001L, 1L, 1L,
                "GPU-1", "2026-08-30T23:00", "2026-08-31T00:00",
                "BOOKING_RESERVED", "2026-08-30T22:00:00Z"
        );
    }
}

