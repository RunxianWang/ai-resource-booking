package com.wrx.booking.domain;

import java.time.LocalDateTime;

public record MessageLog(
        Long id,
        Long bookingId,
        String messageKey,
        String topic,
        String eventType,
        String payload,
        String status,
        Integer retryCount,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
