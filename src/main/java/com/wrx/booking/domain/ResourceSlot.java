package com.wrx.booking.domain;

import java.time.LocalDateTime;

public record ResourceSlot(
        Long id,
        Long machineId,
        String resourceName,
        String resourceType,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer totalCount,
        Integer availableCount,
        String status
) {
}
