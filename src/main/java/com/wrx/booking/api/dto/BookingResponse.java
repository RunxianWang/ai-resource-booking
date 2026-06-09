package com.wrx.booking.api.dto;

import com.wrx.booking.support.ErrorCode;
import com.wrx.booking.support.TraceContext;

public record BookingResponse(
        String code,
        String message,
        String reason,
        String traceId,
        Long bookingId,
        Long userId,
        Long slotId
) {
    public static BookingResponse success(Long bookingId, Long userId, Long slotId) {
        return new BookingResponse(
                ErrorCode.SUCCESS.code(),
                "预约成功",
                null,
                TraceContext.traceId(),
                bookingId,
                userId,
                slotId
        );
    }

    public static BookingResponse fail(String code, String message, Long userId, Long slotId) {
        return new BookingResponse(code, message, message, TraceContext.traceId(), null, userId, slotId);
    }

    public static BookingResponse fail(ErrorCode code, String reason, Long userId, Long slotId) {
        return new BookingResponse(
                code.code(),
                code.message(),
                reason,
                TraceContext.traceId(),
                null,
                userId,
                slotId
        );
    }
}
