package com.wrx.booking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String BOOKING_SUCCESS_TOPIC = "booking-success-topic";

    @Bean
    public NewTopic bookingSuccessTopic() {
        return TopicBuilder.name(BOOKING_SUCCESS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
