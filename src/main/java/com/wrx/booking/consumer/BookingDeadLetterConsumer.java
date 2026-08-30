package com.wrx.booking.consumer;

import com.wrx.booking.config.KafkaConfig;
import com.wrx.booking.domain.DeadLetterLog;
import com.wrx.booking.repository.DeadLetterLogRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class BookingDeadLetterConsumer {

    private static final String DLT_GROUP = "booking-success-dlt-consumer";
    private final DeadLetterLogRepository repository;

    public BookingDeadLetterConsumer(DeadLetterLogRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = KafkaConfig.BOOKING_SUCCESS_DLT, groupId = DLT_GROUP)
    public void onMessage(ConsumerRecord<String, String> record) {
        repository.insertIgnore(new DeadLetterLog(
                null,
                record.key(),
                "booking-success-consumer",
                headerText(record, KafkaHeaders.DLT_ORIGINAL_TOPIC, KafkaConfig.BOOKING_SUCCESS_TOPIC),
                headerInt(record, KafkaHeaders.DLT_ORIGINAL_PARTITION, record.partition()),
                headerLong(record, KafkaHeaders.DLT_ORIGINAL_OFFSET, record.offset()),
                record.value(),
                headerText(record, KafkaHeaders.DLT_EXCEPTION_FQCN, null),
                headerText(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE, null),
                "PENDING",
                3,
                0,
                null,
                null
        ));
    }

    private String headerText(ConsumerRecord<?, ?> record, String name, String fallback) {
        Header header = record.headers().lastHeader(name);
        return header == null ? fallback : new String(header.value(), StandardCharsets.UTF_8);
    }

    private int headerInt(ConsumerRecord<?, ?> record, String name, int fallback) {
        Header header = record.headers().lastHeader(name);
        if (header == null) return fallback;
        byte[] value = header.value();
        if (value.length == 4) {
            return java.nio.ByteBuffer.wrap(value).getInt();
        }
        try {
            return Integer.parseInt(new String(value, StandardCharsets.UTF_8));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long headerLong(ConsumerRecord<?, ?> record, String name, long fallback) {
        Header header = record.headers().lastHeader(name);
        if (header == null) return fallback;
        byte[] value = header.value();
        if (value.length == 8) {
            return java.nio.ByteBuffer.wrap(value).getLong();
        }
        try {
            return Long.parseLong(new String(value, StandardCharsets.UTF_8));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

