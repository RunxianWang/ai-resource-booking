package com.wrx.booking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    public static final String BOOKING_SUCCESS_TOPIC = "booking-success-topic";
    public static final String BOOKING_SUCCESS_DLT = BOOKING_SUCCESS_TOPIC + ".DLT";

    @Bean
    public NewTopic bookingSuccessTopic() {
        return TopicBuilder.name(BOOKING_SUCCESS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic bookingSuccessDltTopic() {
        return TopicBuilder.name(BOOKING_SUCCESS_DLT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(BOOKING_SUCCESS_DLT, record.partition())
        );
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
    }
}
