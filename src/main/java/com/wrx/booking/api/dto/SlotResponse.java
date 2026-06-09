package com.wrx.booking.api.dto;

import java.time.LocalDateTime;

public record SlotResponse(
        Long id,
        Long machineId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer totalCount,
        Integer mysqlAvailableCount,
        Integer redisAvailableCount
) {
}
