package com.wrx.booking.service;

import com.wrx.booking.api.dto.VerifyResponse;
import com.wrx.booking.consumer.BookingSuccessConsumer;
import com.wrx.booking.domain.ResourceSlot;
import com.wrx.booking.repository.BookingRecordRepository;
import com.wrx.booking.repository.ConsumeLogRepository;
import com.wrx.booking.repository.MessageLogRepository;
import com.wrx.booking.repository.ResourceSlotRepository;
import com.wrx.booking.support.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VerifyService {

    private static final Logger log = LoggerFactory.getLogger(VerifyService.class);

    private final ResourceSlotRepository resourceSlotRepository;
    private final BookingRecordRepository bookingRecordRepository;
    private final MessageLogRepository messageLogRepository;
    private final ConsumeLogRepository consumeLogRepository;

    public VerifyService(
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

    public VerifyResponse verify(Long slotId) {
        long start = System.currentTimeMillis();
        ResourceSlot slot = resourceSlotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("slot not found: " + slotId));

        int successBookingCount = bookingRecordRepository.countSuccessBySlot(slotId);
        int messageLogCount = messageLogRepository.countBookingSuccessBySlot(slotId);
        int sentMessageLogCount = messageLogRepository.countBookingSuccessBySlotAndStatus(slotId, MessageLogRepository.STATUS_SENT);
        int consumeLogCount = consumeLogRepository.countSuccessBySlot(slotId, BookingSuccessConsumer.CONSUMER_GROUP);
        int createdBookingCount = bookingRecordRepository.countBySlot(slotId);
        boolean stockConsistent = successBookingCount + slot.availableCount() == slot.totalCount();
        boolean messageConsistent = createdBookingCount == messageLogCount
                && messageLogCount == sentMessageLogCount
                && sentMessageLogCount == consumeLogCount;

        log.info(
                "event=slot.verify traceId={} slotId={} total={} mysqlAvailable={} successBookingCount={} messageLogCount={} sentMessageLogCount={} consumeLogCount={} stockConsistent={} messageConsistent={} costMs={}",
                TraceContext.traceId(),
                slotId,
                slot.totalCount(),
                slot.availableCount(),
                successBookingCount,
                messageLogCount,
                sentMessageLogCount,
                consumeLogCount,
                stockConsistent,
                messageConsistent,
                System.currentTimeMillis() - start
        );

        return new VerifyResponse(
                slot.id(),
                slot.totalCount(),
                slot.availableCount(),
                successBookingCount,
                messageLogCount,
                sentMessageLogCount,
                consumeLogCount,
                stockConsistent,
                messageConsistent
        );
    }
}
