package com.wrx.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrx.booking.api.dto.BookingCancelResponse;
import com.wrx.booking.api.dto.BookingResponse;
import com.wrx.booking.api.dto.SlotBookingResponse;
import com.wrx.booking.api.dto.UserBookingResponse;
import com.wrx.booking.config.KafkaConfig;
import com.wrx.booking.domain.BookingCanceledEvent;
import com.wrx.booking.domain.BookingRecord;
import com.wrx.booking.domain.BookingSuccessEvent;
import com.wrx.booking.domain.ResourceSlot;
import com.wrx.booking.repository.BookingRecordRepository;
import com.wrx.booking.repository.MessageLogRepository;
import com.wrx.booking.repository.ResourceSlotRepository;
import com.wrx.booking.support.ErrorCode;
import com.wrx.booking.support.RedisKeys;
import com.wrx.booking.support.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final String BOOKING_RESERVED_EVENT_TYPE = "BOOKING_RESERVED";
    private static final String BOOKING_CANCELLED_EVENT_TYPE = "BOOKING_CANCELLED";
    private static final int SLOT_BOOKING_QUERY_LIMIT = 50;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> reserveBookingScript;
    private final ResourceSlotRepository resourceSlotRepository;
    private final BookingRecordRepository bookingRecordRepository;
    private final MessageLogRepository messageLogRepository;
    private final ObjectMapper objectMapper;

    public BookingService(
            StringRedisTemplate redisTemplate,
            DefaultRedisScript<Long> reserveBookingScript,
            ResourceSlotRepository resourceSlotRepository,
            BookingRecordRepository bookingRecordRepository,
            MessageLogRepository messageLogRepository,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.reserveBookingScript = reserveBookingScript;
        this.resourceSlotRepository = resourceSlotRepository;
        this.bookingRecordRepository = bookingRecordRepository;
        this.messageLogRepository = messageLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BookingResponse book(Long userId, Long slotId) {
        long start = System.currentTimeMillis();
        log.info(
                "event=booking.start traceId={} userId={} slotId={}",
                TraceContext.traceId(),
                userId,
                slotId
        );

        ResourceSlot slot = resourceSlotRepository.findById(slotId)
                .orElse(null);
        if (slot == null) {
            return fail(ErrorCode.RESOURCE_NOT_FOUND, "slot not found: " + slotId, userId, slotId, start);
        }
        if (bookingRecordRepository.existsByUserAndSlot(userId, slotId)) {
            return fail(ErrorCode.DUPLICATE_BOOKING, "MySQL booking_record already has user slot record", userId, slotId, start);
        }

        Long redisResult = executeRedisReserve(userId, slotId);
        log.info(
                "event=booking.redis.reserve traceId={} userId={} slotId={} result={}",
                TraceContext.traceId(),
                userId,
                slotId,
                redisResult
        );

        if (redisResult == null) {
            return fail(ErrorCode.REDIS_ERROR, "Redis Lua returned null", userId, slotId, start);
        }
        if (redisResult == -2L) {
            return fail(ErrorCode.NOT_WARMED_UP, "Redis inventory key not found, warm up the slot first", userId, slotId, start);
        }
        if (redisResult == -1L) {
            return fail(ErrorCode.DUPLICATE_BOOKING, "Redis booked user set rejected duplicate user", userId, slotId, start);
        }
        if (redisResult == 0L) {
            return fail(ErrorCode.SOLD_OUT, "Redis inventory is less than or equal to zero", userId, slotId, start);
        }

        try {
            int affectedRows = resourceSlotRepository.reserveAvailableSlot(slotId);
            log.info(
                    "event=booking.mysql.decrement traceId={} userId={} slotId={} affectedRows={}",
                    TraceContext.traceId(),
                    userId,
                    slotId,
                    affectedRows
            );

            if (affectedRows == 0) {
                compensateRedis(slotId, userId);
                return fail(ErrorCode.SOLD_OUT, "MySQL slot is not available or already expired", userId, slotId, start);
            }

            Long bookingId = bookingRecordRepository.insertReserved(userId, slotId, slot.machineId());
            log.info(
                    "event=booking.record.created traceId={} userId={} slotId={} bookingId={}",
                    TraceContext.traceId(),
                    userId,
                    slotId,
                    bookingId
            );

            String messageKey = "booking:" + bookingId + ":reserved";
            BookingSuccessEvent event = new BookingSuccessEvent(
                    messageKey,
                    bookingId,
                    userId,
                    slotId,
                    slot.machineId(),
                    slot.resourceName(),
                    slot.startTime().toString(),
                    slot.endTime().toString(),
                    BOOKING_RESERVED_EVENT_TYPE,
                    Instant.now().toString()
            );
            String payload = toJson(event);
            messageLogRepository.insertInit(
                    bookingId,
                    messageKey,
                    KafkaConfig.BOOKING_SUCCESS_TOPIC,
                    BOOKING_RESERVED_EVENT_TYPE,
                    payload
            );
            log.info(
                    "event=booking.message_log.created traceId={} userId={} slotId={} bookingId={} messageKey={}",
                    TraceContext.traceId(),
                    userId,
                    slotId,
                    bookingId,
                    messageKey
            );

            log.info(
                    "event=booking.success traceId={} userId={} slotId={} bookingId={} messageKey={} costMs={}",
                    TraceContext.traceId(),
                    userId,
                    slotId,
                    bookingId,
                    messageKey,
                    System.currentTimeMillis() - start
            );
            return BookingResponse.success(bookingId, userId, slotId);
        } catch (DuplicateKeyException e) {
            compensateRedis(slotId, userId);
            return fail(ErrorCode.DUPLICATE_BOOKING, "MySQL unique key uk_user_slot rejected duplicate booking", userId, slotId, start);
        } catch (RuntimeException e) {
            compensateRedis(slotId, userId);
            log.error(
                    "event=booking.exception traceId={} userId={} slotId={} exception={} reason={}",
                    TraceContext.traceId(),
                    userId,
                    slotId,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e
            );
            throw e;
        }
    }

    private Long executeRedisReserve(Long userId, Long slotId) {
        String availableKey = RedisKeys.slotAvailable(slotId);
        String bookedUsersKey = RedisKeys.slotBookedUsers(slotId);

        return redisTemplate.execute(
                reserveBookingScript,
                List.of(availableKey, bookedUsersKey),
                String.valueOf(userId)
        );
    }

    private void compensateRedis(Long slotId, Long userId) {
        redisTemplate.opsForValue().increment(RedisKeys.slotAvailable(slotId));
        redisTemplate.opsForSet().remove(RedisKeys.slotBookedUsers(slotId), String.valueOf(userId));
        log.warn(
                "event=booking.redis.compensate traceId={} userId={} slotId={}",
                TraceContext.traceId(),
                userId,
                slotId
        );
    }

    private String toJson(BookingSuccessEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serialize booking event failed", e);
        }
    }

    private String toJson(BookingCanceledEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serialize booking cancel event failed", e);
        }
    }

    private BookingResponse fail(ErrorCode code, String reason, Long userId, Long slotId, long start) {
        log.warn(
                "event=booking.fail traceId={} code={} userId={} slotId={} reason={} costMs={}",
                TraceContext.traceId(),
                code.code(),
                userId,
                slotId,
                reason,
                System.currentTimeMillis() - start
        );
        return BookingResponse.fail(code, reason, userId, slotId);
    }

    @Transactional
    public BookingCancelResponse cancel(Long bookingId, Long currentUserId) {
        long start = System.currentTimeMillis();
        BookingRecord booking = bookingRecordRepository.findById(bookingId)
                .orElse(null);
        if (booking == null) {
            log.warn(
                    "event=booking.cancel.not_found traceId={} bookingId={} costMs={}",
                    TraceContext.traceId(),
                    bookingId,
                    System.currentTimeMillis() - start
            );
            return BookingCancelResponse.fail(
                    "BOOKING_NOT_FOUND",
                    "预约记录不存在",
                    "booking not found: " + bookingId,
                    bookingId,
                    null,
                    null
            );
        }

        if (!currentUserId.equals(booking.userId())) {
            log.warn(
                    "event=booking.cancel.forbidden traceId={} bookingId={} ownerUserId={} currentUserId={} slotId={} costMs={}",
                    TraceContext.traceId(),
                    bookingId,
                    booking.userId(),
                    currentUserId,
                    booking.slotId(),
                    System.currentTimeMillis() - start
            );
            return BookingCancelResponse.fail(
                    "BOOKING_FORBIDDEN",
                    "不能取消其他用户的预约",
                    "booking owner does not match current demo user",
                    bookingId,
                    currentUserId,
                    booking.slotId()
            );
        }

        int affectedRows = bookingRecordRepository.cancelReserved(bookingId, currentUserId);
        if (affectedRows == 0) {
            log.info(
                    "event=booking.cancel.skipped traceId={} bookingId={} userId={} slotId={} status={} costMs={}",
                    TraceContext.traceId(),
                    bookingId,
                    currentUserId,
                    booking.slotId(),
                    booking.status(),
                    System.currentTimeMillis() - start
            );
            return BookingCancelResponse.fail(
                    "BOOKING_CANCEL_SKIPPED",
                    "预约不可取消、已取消或已结束",
                    "only unexpired RESERVED booking can be canceled",
                    bookingId,
                    currentUserId,
                    booking.slotId()
            );
        }

        resourceSlotRepository.releaseReservedSlot(booking.slotId());
        redisTemplate.opsForValue().set(RedisKeys.slotAvailable(booking.slotId()), "1");
        redisTemplate.opsForSet().remove(RedisKeys.slotBookedUsers(booking.slotId()), String.valueOf(booking.userId()));

        ResourceSlot slot = resourceSlotRepository.findById(booking.slotId())
                .orElse(null);
        String messageKey = "booking:" + bookingId + ":cancelled";
        BookingCanceledEvent event = new BookingCanceledEvent(
                messageKey,
                bookingId,
                booking.userId(),
                booking.slotId(),
                slot == null ? booking.machineId() : slot.machineId(),
                slot == null ? null : slot.resourceName(),
                slot == null ? null : slot.startTime().toString(),
                slot == null ? null : slot.endTime().toString(),
                BOOKING_CANCELLED_EVENT_TYPE,
                Instant.now().toString()
        );
        messageLogRepository.insertInit(
                bookingId,
                messageKey,
                KafkaConfig.BOOKING_SUCCESS_TOPIC,
                BOOKING_CANCELLED_EVENT_TYPE,
                toJson(event)
        );

        log.info(
                "event=booking.cancel.success traceId={} bookingId={} userId={} slotId={} messageKey={} costMs={}",
                TraceContext.traceId(),
                bookingId,
                booking.userId(),
                booking.slotId(),
                messageKey,
                System.currentTimeMillis() - start
        );
        return BookingCancelResponse.success(bookingId, booking.userId(), booking.slotId());
    }

    @Transactional(readOnly = true)
    public List<UserBookingResponse> listUserBookings(Long userId) {
        return bookingRecordRepository.findByUserId(userId).stream()
                .map(this::toUserBookingResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SlotBookingResponse> listSlotBookings(Long slotId) {
        return bookingRecordRepository.findLatestBySlot(slotId, SLOT_BOOKING_QUERY_LIMIT).stream()
                .map(this::toSlotBookingResponse)
                .toList();
    }

    private UserBookingResponse toUserBookingResponse(BookingRecord booking) {
        return new UserBookingResponse(
                booking.id(),
                booking.userId(),
                booking.slotId(),
                resourceName(booking.slotId()),
                booking.status(),
                booking.createdAt()
        );
    }

    private SlotBookingResponse toSlotBookingResponse(BookingRecord booking) {
        return new SlotBookingResponse(
                booking.id(),
                booking.userId(),
                booking.slotId(),
                resourceName(booking.slotId()),
                booking.status(),
                booking.createdAt()
        );
    }

    private String resourceName(Long slotId) {
        return resourceSlotRepository.findById(slotId)
                .map(ResourceSlot::resourceName)
                .orElse("GPU Slot " + slotId);
    }
}
