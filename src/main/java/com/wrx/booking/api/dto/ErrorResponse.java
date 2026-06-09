package com.wrx.booking.api.dto;

import java.time.Instant;

public record ErrorResponse(
        String code,
        String message,
        String reason,
        String traceId,
        String path,
        Instant timestamp
) {
}
