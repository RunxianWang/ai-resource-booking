package com.wrx.booking.service;

import com.wrx.booking.api.dto.BookingCancelResponse;
import com.wrx.booking.api.dto.BookingResponse;
import com.wrx.booking.api.dto.SlotBookingResponse;
import com.wrx.booking.api.dto.UserBookingResponse;
import com.wrx.booking.domain.BookingRecord;
import com.wrx.booking.domain.ResourceSlot;
import com.wrx.booking.repository.BookingRecordRepository;
import com.wrx.booking.repository.ResourceSlotRepository;
import com.wrx.booking.support.ErrorCode;
import com.wrx.booking.support.RedisKeys;
import com.wrx.booking.support.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final int SLOT_BOOKING_QUERY_LIMIT = 50;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> reserveBookingScript;
    private final DefaultRedisScript<Long> compensateBookingScript;
    private final ResourceSlotRepository resourceSlotRepository;
    private final BookingRecordRepository bookingRecordRepository;
    private final BookingTransactionService transactionService;

    public BookingService(StringRedisTemplate redisTemplate,
                          @Qualifier("reserveBookingScript") DefaultRedisScript<Long> reserveBookingScript,
                          @Qualifier("compensateBookingScript") DefaultRedisScript<Long> compensateBookingScript,
                          ResourceSlotRepository resourceSlotRepository,
                          BookingRecordRepository bookingRecordRepository,
                          BookingTransactionService transactionService) {
        this.redisTemplate = redisTemplate;
        this.reserveBookingScript = reserveBookingScript;
        this.compensateBookingScript = compensateBookingScript;
        this.resourceSlotRepository = resourceSlotRepository;
        this.bookingRecordRepository = bookingRecordRepository;
        this.transactionService = transactionService;
    }

    public BookingResponse book(Long userId, Long slotId) {
        long start = System.currentTimeMillis();
        ResourceSlot slot = resourceSlotRepository.findById(slotId).orElse(null);
        if (slot == null) return BookingResponse.fail(ErrorCode.RESOURCE_NOT_FOUND, "slot not found: " + slotId, userId, slotId);
        if (bookingRecordRepository.existsByUserAndSlot(userId, slotId)) {
            return BookingResponse.fail(ErrorCode.DUPLICATE_BOOKING, "MySQL booking_record already has user slot record", userId, slotId);
        }

        Long result = redisTemplate.execute(reserveBookingScript,
                List.of(RedisKeys.slotAvailable(slotId), RedisKeys.slotBookedUsers(slotId)), String.valueOf(userId));
        if (result == null) return BookingResponse.fail(ErrorCode.REDIS_ERROR, "Redis Lua returned null", userId, slotId);
        if (result == -2L) return BookingResponse.fail(ErrorCode.NOT_WARMED_UP, "Redis inventory key not found, warm up the slot first", userId, slotId);
        if (result == -1L) return BookingResponse.fail(ErrorCode.DUPLICATE_BOOKING, "Redis booked user set rejected duplicate user", userId, slotId);
        if (result == 0L) return BookingResponse.fail(ErrorCode.SOLD_OUT, "Redis inventory is less than or equal to zero", userId, slotId);

        try {
            BookingResponse response = transactionService.createBooking(userId, slotId);
            if (!ErrorCode.SUCCESS.code().equals(response.code())) compensateRedis(slot, userId);
            log.info("event=booking.result traceId={} userId={} slotId={} code={} costMs={}", TraceContext.traceId(), userId, slotId, response.code(), System.currentTimeMillis() - start);
            return response;
        } catch (RuntimeException e) {
            compensateRedis(slot, userId);
            log.error("event=booking.exception traceId={} userId={} slotId={} reason={}", TraceContext.traceId(), userId, slotId, e.getMessage(), e);
            throw e;
        }
    }

    private void compensateRedis(ResourceSlot slot, Long userId) {
        Long changed = redisTemplate.execute(compensateBookingScript,
                List.of(RedisKeys.slotAvailable(slot.id()), RedisKeys.slotBookedUsers(slot.id())),
                String.valueOf(userId), String.valueOf(slot.totalCount()));
        log.warn("event=booking.redis.compensate traceId={} userId={} slotId={} changed={}", TraceContext.traceId(), userId, slot.id(), changed);
    }

    public BookingCancelResponse cancel(Long bookingId, Long currentUserId) {
        BookingCancelResponse response = transactionService.cancelBooking(bookingId, currentUserId);
        if (ErrorCode.SUCCESS.code().equals(response.code())) {
            ResourceSlot slot = resourceSlotRepository.findById(response.slotId()).orElse(null);
            if (slot != null) {
                redisTemplate.execute(compensateBookingScript,
                        List.of(RedisKeys.slotAvailable(slot.id()), RedisKeys.slotBookedUsers(slot.id())),
                        String.valueOf(response.userId()), String.valueOf(slot.totalCount()));
            }
        }
        return response;
    }

    public List<UserBookingResponse> listUserBookings(Long userId) {
        return bookingRecordRepository.findByUserId(userId).stream().map(this::toUserBookingResponse).toList();
    }

    public List<SlotBookingResponse> listSlotBookings(Long slotId) {
        return bookingRecordRepository.findLatestBySlot(slotId, SLOT_BOOKING_QUERY_LIMIT).stream().map(this::toSlotBookingResponse).toList();
    }

    private UserBookingResponse toUserBookingResponse(BookingRecord booking) {
        return new UserBookingResponse(booking.id(), booking.userId(), booking.slotId(), resourceName(booking.slotId()), booking.status(), booking.createdAt());
    }

    private SlotBookingResponse toSlotBookingResponse(BookingRecord booking) {
        return new SlotBookingResponse(booking.id(), booking.userId(), booking.slotId(), resourceName(booking.slotId()), booking.status(), booking.createdAt());
    }

    private String resourceName(Long slotId) {
        return resourceSlotRepository.findById(slotId).map(ResourceSlot::resourceName).orElse("GPU Slot " + slotId);
    }
}
