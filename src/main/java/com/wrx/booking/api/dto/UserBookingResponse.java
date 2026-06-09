package com.wrx.booking.api.dto;

import java.time.LocalDateTime;

public record UserBookingResponse(
        Long bookingId,
        Long userId,
        Long slotId,
        String resourceName,
        String status,
        LocalDateTime createdAt
) {
}
