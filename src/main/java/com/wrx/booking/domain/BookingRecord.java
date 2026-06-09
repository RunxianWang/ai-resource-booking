package com.wrx.booking.domain;

import java.time.LocalDateTime;

public record BookingRecord(
        Long id,
        Long userId,
        Long slotId,
        Long machineId,
        String status,
        LocalDateTime createdAt
) {
}
