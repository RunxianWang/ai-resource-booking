package com.wrx.booking.api.dto;

import java.time.LocalDateTime;

public record SlotListResponse(
        Long id,
        String resourceName,
        String resourceType,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer totalCount,
        Integer availableCount,
        String status
) {
}
