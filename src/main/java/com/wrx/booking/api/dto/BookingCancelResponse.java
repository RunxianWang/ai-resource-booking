package com.wrx.booking.api.dto;

import com.wrx.booking.support.TraceContext;

public record BookingCancelResponse(
        String code,
        String message,
        String reason,
        String traceId,
        Long bookingId,
        Long userId,
        Long slotId
) {
    public static BookingCancelResponse success(Long bookingId, Long userId, Long slotId) {
        return new BookingCancelResponse(
                "SUCCESS",
                "取消预约成功",
                null,
                TraceContext.traceId(),
                bookingId,
                userId,
                slotId
        );
    }

    public static BookingCancelResponse fail(String code, String message, String reason, Long bookingId, Long userId, Long slotId) {
        return new BookingCancelResponse(
                code,
                message,
                reason,
                TraceContext.traceId(),
                bookingId,
                userId,
                slotId
        );
    }
}
