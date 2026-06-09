package com.wrx.booking.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrx.booking.config.KafkaConfig;
import com.wrx.booking.domain.BookingSuccessEvent;
import com.wrx.booking.repository.ConsumeLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class BookingSuccessConsumer {

    public static final String CONSUMER_GROUP = "booking-success-consumer";

    private static final Logger log = LoggerFactory.getLogger(BookingSuccessConsumer.class);

    private final ConsumeLogRepository consumeLogRepository;
    private final ObjectMapper objectMapper;

    public BookingSuccessConsumer(
            ConsumeLogRepository consumeLogRepository,
            ObjectMapper objectMapper
    ) {
        this.consumeLogRepository = consumeLogRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaConfig.BOOKING_SUCCESS_TOPIC, groupId = CONSUMER_GROUP)
    public void onMessage(
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            String payload
    ) {
        BookingSuccessEvent event = parseEvent(key, payload);
        String messageKey = event.messageKey();
        try {
            consumeLogRepository.insertSuccess(messageKey, CONSUMER_GROUP);
        } catch (DuplicateKeyException e) {
            log.info(
                    "event=kafka.booking_event.duplicate_skip topic={} group={} key={} eventType={} payload={}",
                    KafkaConfig.BOOKING_SUCCESS_TOPIC,
                    CONSUMER_GROUP,
                    messageKey,
                    event.eventType(),
                    payload
            );
            return;
        }

        if ("BOOKING_CANCELLED".equals(event.eventType())) {
            log.info(
                    "event=kafka.booking_cancelled.consumed topic={} group={} key={} bookingId={} userId={} slotId={} machineId={} machineName={} startTime={} endTime={} payload={}",
                    KafkaConfig.BOOKING_SUCCESS_TOPIC,
                    CONSUMER_GROUP,
                    messageKey,
                    event.bookingId(),
                    event.userId(),
                    event.slotId(),
                    event.machineId(),
                    event.machineName(),
                    event.startTime(),
                    event.endTime(),
                    payload
            );
            return;
        }

        log.info(
                "event=kafka.booking_reserved.consumed topic={} group={} key={} bookingId={} userId={} slotId={} machineId={} machineName={} startTime={} endTime={} payload={}",
                KafkaConfig.BOOKING_SUCCESS_TOPIC,
                CONSUMER_GROUP,
                messageKey,
                event.bookingId(),
                event.userId(),
                event.slotId(),
                event.machineId(),
                event.machineName(),
                event.startTime(),
                event.endTime(),
                payload
        );
        log.info(
                "event=booking_reserved.processed group={} messageKey={}",
                CONSUMER_GROUP,
                messageKey
        );
    }

    private BookingSuccessEvent parseEvent(String key, String payload) {
        try {
            BookingSuccessEvent event = objectMapper.readValue(payload, BookingSuccessEvent.class);
            if (event.messageKey() != null && !event.messageKey().isBlank()) {
                return event;
            }
        } catch (JsonProcessingException e) {
            log.warn(
                    "event=kafka.booking_success.parse_message_key_failed key={} reason={}",
                    key,
                    e.getMessage()
            );
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("booking success message key is blank");
        }
        return new BookingSuccessEvent(key, null, null, null, null, null, null, null, "UNKNOWN", null);
    }
}
