package com.wrx.booking.domain;

public record BookingCanceledEvent(
        String messageKey,
        Long bookingId,
        Long userId,
        Long slotId,
        Long machineId,
        String machineName,
        String startTime,
        String endTime,
        String eventType,
        String createdAt
) {
}
