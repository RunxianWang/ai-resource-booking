package com.wrx.booking.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrx.booking.config.KafkaConfig;
import com.wrx.booking.domain.BookingSuccessEvent;
import com.wrx.booking.service.BookingEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookingSuccessConsumer {

    public static final String CONSUMER_GROUP = "booking-success-consumer";

    private static final Logger log = LoggerFactory.getLogger(BookingSuccessConsumer.class);

    private final BookingEventHandler bookingEventHandler;
    private final ObjectMapper objectMapper;

    public BookingSuccessConsumer(BookingEventHandler bookingEventHandler, ObjectMapper objectMapper) {
        this.bookingEventHandler = bookingEventHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaConfig.BOOKING_SUCCESS_TOPIC, groupId = CONSUMER_GROUP)
    public void onMessage(String payload) {
        BookingSuccessEvent event = parseEvent(payload);
        bookingEventHandler.handle(event, CONSUMER_GROUP);
        log.info("event=kafka.booking_event.consumed topic={} group={} key={} eventType={} payload={}",
                KafkaConfig.BOOKING_SUCCESS_TOPIC, CONSUMER_GROUP, event.messageKey(), event.eventType(), payload);
    }

    private BookingSuccessEvent parseEvent(String payload) {
        try {
            BookingSuccessEvent event = objectMapper.readValue(payload, BookingSuccessEvent.class);
            if (event.messageKey() != null && !event.messageKey().isBlank()) {
                return event;
            }
        } catch (JsonProcessingException e) {
            log.warn("event=kafka.booking_success.parse_failed reason={}", e.getMessage());
        }
        throw new IllegalArgumentException("booking event payload is invalid");
    }
}
