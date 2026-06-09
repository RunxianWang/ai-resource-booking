package com.wrx.booking.service;

import com.wrx.booking.domain.MessageLog;
import com.wrx.booking.repository.MessageLogRepository;
import com.wrx.booking.support.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxSender {

    private static final Logger log = LoggerFactory.getLogger(OutboxSender.class);
    private static final int BATCH_SIZE = 50;
    private static final int SEND_TIMEOUT_SECONDS = 10;

    private final MessageLogRepository messageLogRepository;
    private final BookingEventPublisher bookingEventPublisher;

    public OutboxSender(
            MessageLogRepository messageLogRepository,
            BookingEventPublisher bookingEventPublisher
    ) {
        this.messageLogRepository = messageLogRepository;
        this.bookingEventPublisher = bookingEventPublisher;
    }

    @Scheduled(fixedDelayString = "${booking.outbox.sender.fixed-delay-ms:1000}")
    public void sendPendingMessages() {
        List<MessageLog> pendingMessages = messageLogRepository.findPending(BATCH_SIZE);
        if (pendingMessages.isEmpty()) {
            return;
        }

        log.info(
                "event=outbox.sender.batch.start traceId={} size={}",
                TraceContext.traceId(),
                pendingMessages.size()
        );

        for (MessageLog message : pendingMessages) {
            sendOne(message);
        }
    }

    private void sendOne(MessageLog message) {
        try {
            SendResult<String, String> result = bookingEventPublisher
                    .publish(message.topic(), message.messageKey(), message.payload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            messageLogRepository.markSent(message.id());
            log.info(
                    "event=outbox.sender.sent traceId={} messageLogId={} topic={} key={} partition={} offset={}",
                    TraceContext.traceId(),
                    message.id(),
                    message.topic(),
                    message.messageKey(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
        } catch (Exception e) {
            String lastError = truncateError(e);
            messageLogRepository.markFailed(message.id(), lastError);
            log.warn(
                    "event=outbox.sender.failed traceId={} messageLogId={} topic={} key={} retryCount={} reason={}",
                    TraceContext.traceId(),
                    message.id(),
                    message.topic(),
                    message.messageKey(),
                    message.retryCount() + 1,
                    lastError
            );
        }
    }

    private String truncateError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
