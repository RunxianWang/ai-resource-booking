package com.wrx.booking.api.dto;

import com.wrx.booking.support.ErrorCode;
import com.wrx.booking.support.TraceContext;
import java.util.List;

public record BookingResponse(
        String code,
        String message,
        String reason,
        String traceId,
        Long bookingId,
        Long userId,
        Long slotId,
        List<Long> bookingIds,
        List<Long> slotIds
) {
    public static BookingResponse success(Long bookingId, Long userId, Long slotId) {
        return new BookingResponse(
                ErrorCode.SUCCESS.code(),
                "预约成功",
                null,
                TraceContext.traceId(),
                bookingId,
                userId,
                slotId,
                List.of(bookingId),
                List.of(slotId)
        );
    }

    public static BookingResponse success(List<Long> bookingIds, Long userId, List<Long> slotIds) {
        return new BookingResponse(ErrorCode.SUCCESS.code(), "预约成功", null, TraceContext.traceId(),
                bookingIds.get(0), userId, slotIds.get(0), bookingIds, slotIds);
    }

    public static BookingResponse fail(String code, String message, Long userId, Long slotId) {
        return new BookingResponse(code, message, message, TraceContext.traceId(), null, userId, slotId, List.of(), List.of(slotId));
    }

    public static BookingResponse fail(ErrorCode code, String reason, Long userId, Long slotId) {
        return new BookingResponse(
                code.code(),
                code.message(),
                reason,
                TraceContext.traceId(),
                null,
                userId,
                slotId,
                List.of(),
                List.of(slotId)
        );
    }
}
