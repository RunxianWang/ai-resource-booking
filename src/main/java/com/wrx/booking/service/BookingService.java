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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

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
    private final boolean faultInjectionEnabled;
    private final String faultPoint;

    public BookingService(StringRedisTemplate redisTemplate,
                          @Qualifier("reserveBookingScript") DefaultRedisScript<Long> reserveBookingScript,
                          @Qualifier("compensateBookingScript") DefaultRedisScript<Long> compensateBookingScript,
                          ResourceSlotRepository resourceSlotRepository,
                          BookingRecordRepository bookingRecordRepository,
                          BookingTransactionService transactionService,
                          @Value("${app.fault-injection.enabled:false}") boolean faultInjectionEnabled,
                          @Value("${app.fault-injection.point:none}") String faultPoint) {
        this.redisTemplate = redisTemplate;
        this.reserveBookingScript = reserveBookingScript;
        this.compensateBookingScript = compensateBookingScript;
        this.resourceSlotRepository = resourceSlotRepository;
        this.bookingRecordRepository = bookingRecordRepository;
        this.transactionService = transactionService;
        this.faultInjectionEnabled = faultInjectionEnabled;
        this.faultPoint = faultPoint;
    }

    public BookingResponse book(Long userId, Long slotId) {
        return book(userId, slotId, 1);
    }

    public BookingResponse book(Long userId, Long slotId, Integer requestedDurationHours) {
        long start = System.currentTimeMillis();
        int durationHours = requestedDurationHours == null ? 1 : requestedDurationHours;
        if (durationHours != 1 && durationHours != 2 && durationHours != 4) {
            return BookingResponse.fail(ErrorCode.INVALID_DURATION, "durationHours must be one of 1, 2, 4", userId, slotId);
        }
        ResourceSlot slot = resourceSlotRepository.findById(slotId).orElse(null);
        if (slot == null) return BookingResponse.fail(ErrorCode.RESOURCE_NOT_FOUND, "slot not found: " + slotId, userId, slotId);

        LocalDate today = LocalDate.now();
        LocalDateTime nextHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(1);
        if (!slot.startTime().toLocalDate().equals(today)
                || slot.startTime().isBefore(nextHour)
                || slot.endTime().isAfter(today.plusDays(1).atStartOfDay())) {
            return BookingResponse.fail(ErrorCode.SLOT_NOT_BOOKABLE, "slot must be a complete future slot on today", userId, slotId);
        }

        List<ResourceSlot> slots = new java.util.ArrayList<>();
        for (int i = 0; i < durationHours; i++) {
            ResourceSlot current = i == 0 ? slot : resourceSlotRepository
                    .findByMachineAndStartTime(slot.machineId(), slot.startTime().plusHours(i)).orElse(null);
            if (current != null && bookingRecordRepository.existsByUserAndSlot(userId, current.id())) {
                return BookingResponse.fail(ErrorCode.DUPLICATE_BOOKING,
                        "user already has a booking for one of the requested slots", userId, slotId);
            }
            if (current == null || !current.startTime().equals(slot.startTime().plusHours(i))
                    || !current.endTime().equals(current.startTime().plusHours(1))
                    || !current.endTime().toLocalDate().equals(today)) {
                return BookingResponse.fail(i == 0 ? ErrorCode.SLOT_NOT_BOOKABLE : ErrorCode.NON_CONTIGUOUS_SLOTS,
                        "requested duration has no continuous available slots", userId, slotId);
            }
            if (current.availableCount() <= 0) {
                return BookingResponse.fail(ErrorCode.SOLD_OUT, "slot inventory is exhausted", userId, slotId);
            }
            if (!ResourceSlotRepository.STATUS_AVAILABLE.equals(current.status())) {
                return BookingResponse.fail(i == 0 ? ErrorCode.SLOT_NOT_BOOKABLE : ErrorCode.NON_CONTIGUOUS_SLOTS,
                        "requested duration has no continuous available slots", userId, slotId);
            }
            slots.add(current);
        }

        List<String> keys = new java.util.ArrayList<>();
        slots.forEach(current -> {
            keys.add(RedisKeys.slotAvailable(current.id()));
            keys.add(RedisKeys.slotBookedUsers(current.id()));
        });
        Long result = redisTemplate.execute(reserveBookingScript, keys, String.valueOf(userId));
        if (result == null) return BookingResponse.fail(ErrorCode.REDIS_ERROR, "Redis Lua returned null", userId, slotId);
        if (result == -2L) return BookingResponse.fail(ErrorCode.NOT_WARMED_UP, "Redis inventory key not found, warm up the slot first", userId, slotId);
        if (result == -1L) return BookingResponse.fail(ErrorCode.DUPLICATE_BOOKING, "Redis booked user set rejected duplicate user", userId, slotId);
        if (result == 0L) return BookingResponse.fail(ErrorCode.SOLD_OUT, "Redis inventory is less than or equal to zero", userId, slotId);

        try {
            failIfConfigured("redis-after-reserve");
            BookingResponse response = transactionService.createBooking(userId, slots);
            if (!ErrorCode.SUCCESS.code().equals(response.code())) slots.forEach(current -> compensateRedis(current, userId));
            log.info("event=booking.result traceId={} userId={} slotId={} code={} costMs={}", TraceContext.traceId(), userId, slotId, response.code(), System.currentTimeMillis() - start);
            return response;
        } catch (RuntimeException e) {
            slots.forEach(current -> compensateRedis(current, userId));
            log.error("event=booking.exception traceId={} userId={} slotId={} reason={}", TraceContext.traceId(), userId, slotId, e.getMessage(), e);
            throw e;
        }
    }

    private void failIfConfigured(String point) {
        if (faultInjectionEnabled && point.equalsIgnoreCase(faultPoint)) {
            throw new IllegalStateException("Injected failure at " + point);
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
