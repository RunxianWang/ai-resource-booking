package com.wrx.booking.api.dto;

/**
 * 压测后的一致性校验结果。
 */
public record VerifyResponse(
        Long slotId,
        Integer totalCount,
        Integer mysqlAvailableCount,
        Integer successBookingCount,
        Integer messageLogCount,
        Integer consumedMessageCount,
        Integer consumeLogCount,
        Boolean stockConsistent,
        Boolean messageConsistent
) {
}