package com.wrx.booking.domain;

import java.time.LocalDateTime;

public record ConsumeLog(
        Long id,
        String messageKey,
        String consumerGroup,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
