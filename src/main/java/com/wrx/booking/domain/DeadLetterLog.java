package com.wrx.booking.domain;

import java.time.LocalDateTime;

public record DeadLetterLog(
        Long id,
        String messageKey,
        String consumerGroup,
        String originalTopic,
        Integer originalPartition,
        Long originalOffset,
        String payload,
        String exceptionClass,
        String exceptionMessage,
        String status,
        Integer retryCount,
        Integer replayCount,
        LocalDateTime createdAt,
        LocalDateTime replayedAt
) {
}
