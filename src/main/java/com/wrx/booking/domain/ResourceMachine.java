package com.wrx.booking.domain;

import java.time.LocalDateTime;

public record ResourceMachine(
        Long id,
        String machineName,
        String resourceType,
        String gpuModel,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
