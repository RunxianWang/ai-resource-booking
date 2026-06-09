package com.wrx.booking.service;

import com.wrx.booking.api.dto.DevStateResponse;
import com.wrx.booking.consumer.BookingSuccessConsumer;
import com.wrx.booking.domain.BookingRecord;
import com.wrx.booking.domain.ConsumeLog;
import com.wrx.booking.domain.MessageLog;
import com.wrx.booking.domain.ResourceSlot;
import com.wrx.booking.repository.BookingRecordRepository;
import com.wrx.booking.repository.ConsumeLogRepository;
import com.wrx.booking.repository.MessageLogRepository;
import com.wrx.booking.repository.ResourceSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DevStateService {

    private static final int LATEST_LIMIT = 10;
    private static final String BOOKING_RESERVED_EVENT_TYPE = "BOOKING_RESERVED";
    private static final String BOOKING_CANCELLED_EVENT_TYPE = "BOOKING_CANCELLED";

    private final ResourceSlotRepository resourceSlotRepository;
    private final BookingRecordRepository bookingRecordRepository;
    private final MessageLogRepository messageLogRepository;
    private final ConsumeLogRepository consumeLogRepository;

    public DevStateService(
            ResourceSlotRepository resourceSlotRepository,
            BookingRecordRepository bookingRecordRepository,
            MessageLogRepository messageLogRepository,
            ConsumeLogRepository consumeLogRepository
    ) {
        this.resourceSlotRepository = resourceSlotRepository;
        this.bookingRecordRepository = bookingRecordRepository;
        this.messageLogRepository = messageLogRepository;
        this.consumeLogRepository = consumeLogRepository;
    }

    @Transactional(readOnly = true)
    public DevStateResponse getState(Long slotId) {
        ResourceSlot slot = resourceSlotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("slot not found: " + slotId));

        int successBookingCount = bookingRecordRepository.countSuccessBySlot(slotId);
        int canceledBookingCount = bookingRecordRepository.countCanceledBySlot(slotId);
        int totalBookingCount = bookingRecordRepository.countBySlot(slotId);

        int messageLogCount = messageLogRepository.countBySlot(slotId);
        int successMessageCount = messageLogRepository.countBySlotAndEventType(slotId, BOOKING_RESERVED_EVENT_TYPE);
        int cancelMessageCount = messageLogRepository.countBySlotAndEventType(slotId, BOOKING_CANCELLED_EVENT_TYPE);
        int sentMessageCount = messageLogRepository.countBySlotAndStatus(slotId, MessageLogRepository.STATUS_SENT);
        int initMessageCount = messageLogRepository.countBySlotAndStatus(slotId, MessageLogRepository.STATUS_INIT);
        int failedMessageCount = messageLogRepository.countBySlotAndStatus(slotId, MessageLogRepository.STATUS_FAILED);
        int consumeLogCount = consumeLogRepository.countBySlot(slotId, BookingSuccessConsumer.CONSUMER_GROUP);

        boolean stockConsistent = slot.totalCount() - slot.availableCount() == successBookingCount;
        boolean messageCreatedConsistent = successMessageCount == totalBookingCount
                && cancelMessageCount == canceledBookingCount;
        boolean messageSentConsistent = sentMessageCount == messageLogCount
                && initMessageCount == 0
                && failedMessageCount == 0;
        boolean consumeConsistent = consumeLogCount == sentMessageCount;

        return new DevStateResponse(
                slotId,
                new DevStateResponse.ResourceSlotState(
                        slot.id(),
                        slot.totalCount(),
                        slot.availableCount()
                ),
                new DevStateResponse.BookingSummary(
                        successBookingCount,
                        canceledBookingCount,
                        toBookingStates(bookingRecordRepository.findLatestBySlot(slotId, LATEST_LIMIT))
                ),
                new DevStateResponse.MessageSummary(
                        messageLogCount,
                        successMessageCount,
                        cancelMessageCount,
                        sentMessageCount,
                        initMessageCount,
                        failedMessageCount,
                        toMessageStates(messageLogRepository.findLatestBySlot(slotId, LATEST_LIMIT))
                ),
                new DevStateResponse.ConsumeSummary(
                        consumeLogCount,
                        toConsumeLogStates(consumeLogRepository.findLatestBySlot(slotId, BookingSuccessConsumer.CONSUMER_GROUP, LATEST_LIMIT))
                ),
                new DevStateResponse.Consistency(
                        stockConsistent,
                        messageCreatedConsistent,
                        messageSentConsistent,
                        consumeConsistent
                )
        );
    }

    private List<DevStateResponse.BookingState> toBookingStates(List<BookingRecord> records) {
        return records.stream()
                .map(record -> new DevStateResponse.BookingState(
                        record.id(),
                        record.userId(),
                        record.slotId(),
                        record.status(),
                        record.createdAt()
                ))
                .toList();
    }

    private List<DevStateResponse.MessageState> toMessageStates(List<MessageLog> logs) {
        return logs.stream()
                .map(log -> new DevStateResponse.MessageState(
                        log.id(),
                        log.bookingId(),
                        log.messageKey(),
                        log.topic(),
                        log.eventType(),
                        log.status(),
                        log.retryCount(),
                        log.lastError(),
                        log.createdAt(),
                        log.updatedAt()
                ))
                .toList();
    }

    private List<DevStateResponse.ConsumeLogState> toConsumeLogStates(List<ConsumeLog> logs) {
        return logs.stream()
                .map(log -> new DevStateResponse.ConsumeLogState(
                        log.id(),
                        log.messageKey(),
                        log.consumerGroup(),
                        log.status(),
                        log.createdAt(),
                        log.updatedAt()
                ))
                .toList();
    }
}
