package com.wrx.booking.service;

import com.wrx.booking.config.KafkaConfig;
import com.wrx.booking.support.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public BookingEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, String>> publish(String topic, String key, String payload) {
        long start = System.currentTimeMillis();
        String traceId = TraceContext.traceId();
        log.info(
                "event=kafka.publish.start traceId={} topic={} key={}",
                traceId,
                topic,
                key
        );

        return kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info(
                                "event=kafka.publish.success traceId={} topic={} key={} partition={} offset={} costMs={}",
                                traceId,
                                topic,
                                key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                System.currentTimeMillis() - start
                        );
                    } else {
                        log.error(
                                "event=kafka.publish.fail traceId={} topic={} key={} reason={} costMs={}",
                                traceId,
                                topic,
                                key,
                                ex.getMessage(),
                                System.currentTimeMillis() - start,
                                ex
                        );
                    }
                });
    }

    public CompletableFuture<SendResult<String, String>> publishBookingSuccess(String key, String payload) {
        return publish(KafkaConfig.BOOKING_SUCCESS_TOPIC, key, payload);
    }
}
