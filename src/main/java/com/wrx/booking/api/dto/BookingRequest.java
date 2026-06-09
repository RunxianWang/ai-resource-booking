package com.wrx.booking.api.dto;

import jakarta.validation.constraints.NotNull;

public record BookingRequest(
        Long userId,
        @NotNull Long slotId
) {
}
