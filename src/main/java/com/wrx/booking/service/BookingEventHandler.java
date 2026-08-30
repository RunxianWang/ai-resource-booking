package com.wrx.booking.service;

import com.wrx.booking.domain.BookingSuccessEvent;
import com.wrx.booking.repository.BookingEventRepository;
import com.wrx.booking.repository.ConsumeLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingEventHandler {

    private final ConsumeLogRepository consumeLogRepository;
    private final BookingEventRepository bookingEventRepository;
    private final boolean faultInjectionEnabled;
    private final String faultPoint;

    public BookingEventHandler(
            ConsumeLogRepository consumeLogRepository,
            BookingEventRepository bookingEventRepository,
            @Value("${app.fault-injection.enabled:false}") boolean faultInjectionEnabled,
            @Value("${app.fault-injection.point:none}") String faultPoint
    ) {
        this.consumeLogRepository = consumeLogRepository;
        this.bookingEventRepository = bookingEventRepository;
        this.faultInjectionEnabled = faultInjectionEnabled;
        this.faultPoint = faultPoint;
    }

    @Transactional
    public void handle(BookingSuccessEvent event, String consumerGroup) {
        if (event.bookingId() == null || event.messageKey() == null || event.messageKey().isBlank()) {
            throw new IllegalArgumentException("booking event requires bookingId and messageKey");
        }

        int inserted = consumeLogRepository.insertSuccess(event.messageKey(), consumerGroup);
        if (inserted == 0) {
            return;
        }

        failIfConfigured("consumer-before-process");
        String status = "BOOKING_CANCELLED".equals(event.eventType()) ? "CANCELLED" : "RESERVED";
        failIfConfigured("projection-update");
        bookingEventRepository.upsertProjection(event, status);
        bookingEventRepository.insertAudit(event, consumerGroup);
        failIfConfigured("consume-log-write");
    }

    private void failIfConfigured(String point) {
        if (faultInjectionEnabled && point.equalsIgnoreCase(faultPoint)) {
            throw new IllegalStateException("Injected failure at " + point);
        }
    }
}
