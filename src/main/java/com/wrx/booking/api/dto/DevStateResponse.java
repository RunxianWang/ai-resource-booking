package com.wrx.booking.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DevStateResponse(
        Long slotId,
        ResourceSlotState resourceSlot,
        BookingSummary bookingSummary,
        MessageSummary messageSummary,
        ConsumeSummary consumeSummary,
        Consistency consistency
) {
    public record ResourceSlotState(
            Long id,
            Integer totalCount,
            Integer availableCount
    ) {
    }

    public record BookingSummary(
            Integer successBookingCount,
            Integer canceledBookingCount,
            List<BookingState> latestBookings
    ) {
    }

    public record BookingState(
            Long bookingId,
            Long userId,
            Long slotId,
            String status,
            LocalDateTime createdAt
    ) {
    }

    public record MessageSummary(
            Integer messageLogCount,
            Integer successMessageCount,
            Integer cancelMessageCount,
            Integer sentMessageCount,
            Integer initMessageCount,
            Integer failedMessageCount,
            List<MessageState> latestMessages
    ) {
    }

    public record MessageState(
            Long id,
            Long bookingId,
            String messageKey,
            String topic,
            String eventType,
            String status,
            Integer retryCount,
            String lastError,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ConsumeSummary(
            Integer consumeLogCount,
            List<ConsumeLogState> latestConsumeLogs
    ) {
    }

    public record ConsumeLogState(
            Long id,
            String messageKey,
            String consumerGroup,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record Consistency(
            Boolean stockConsistent,
            Boolean messageCreatedConsistent,
            Boolean messageSentConsistent,
            Boolean consumeConsistent
    ) {
    }
}
