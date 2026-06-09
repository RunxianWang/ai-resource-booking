package com.wrx.booking.service;

import com.wrx.booking.api.dto.SlotListResponse;
import com.wrx.booking.api.dto.SlotResponse;
import com.wrx.booking.domain.ResourceSlot;
import com.wrx.booking.domain.ResourceSlotCatalog;
import com.wrx.booking.repository.BookingRecordRepository;
import com.wrx.booking.repository.ResourceSlotRepository;
import com.wrx.booking.support.RedisKeys;
import com.wrx.booking.support.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SlotService {

    private static final Logger log = LoggerFactory.getLogger(SlotService.class);

    private final ResourceSlotRepository resourceSlotRepository;
    private final BookingRecordRepository bookingRecordRepository;
    private final StringRedisTemplate redisTemplate;

    public SlotService(
            ResourceSlotRepository resourceSlotRepository,
            BookingRecordRepository bookingRecordRepository,
            StringRedisTemplate redisTemplate
    ) {
        this.resourceSlotRepository = resourceSlotRepository;
        this.bookingRecordRepository = bookingRecordRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public List<SlotListResponse> listSlots() {
        return resourceSlotRepository.findAllForCatalog().stream()
                .map(this::toSlotListResponse)
                .toList();
    }

    public SlotResponse querySlot(Long slotId) {
        long start = System.currentTimeMillis();
        ResourceSlot slot = resourceSlotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("slot not found: " + slotId));

        String redisValue = redisTemplate.opsForValue().get(RedisKeys.slotAvailable(slotId));
        Integer redisAvailable = redisValue == null ? null : Integer.valueOf(redisValue);

        log.info(
                "event=slot.query traceId={} slotId={} mysqlAvailable={} redisAvailable={} costMs={}",
                TraceContext.traceId(),
                slotId,
                slot.availableCount(),
                redisAvailable,
                System.currentTimeMillis() - start
        );

        return new SlotResponse(
                slot.id(),
                slot.machineId(),
                slot.startTime(),
                slot.endTime(),
                slot.totalCount(),
                slot.availableCount(),
                redisAvailable
        );
    }

    private SlotListResponse toSlotListResponse(ResourceSlotCatalog slot) {
        return new SlotListResponse(
                slot.id(),
                slot.resourceName(),
                slot.resourceType(),
                slot.startTime(),
                slot.endTime(),
                slot.totalCount(),
                slot.availableCount(),
                slot.status()
        );
    }

    public void warmupSlot(Long slotId) {
        long start = System.currentTimeMillis();
        ResourceSlot slot = resourceSlotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("slot not found: " + slotId));

        String availableKey = RedisKeys.slotAvailable(slotId);
        String bookedUsersKey = RedisKeys.slotBookedUsers(slotId);

        redisTemplate.delete(availableKey);
        redisTemplate.delete(bookedUsersKey);
        redisTemplate.opsForValue().set(availableKey, String.valueOf(slot.availableCount()));

        List<Long> bookedUserIds = bookingRecordRepository.findReservedUserIdsBySlot(slotId);
        if (!bookedUserIds.isEmpty()) {
            String[] userIds = bookedUserIds.stream()
                    .map(String::valueOf)
                    .toArray(String[]::new);
            redisTemplate.opsForSet().add(bookedUsersKey, userIds);
        }

        log.info(
                "event=slot.redis.warmup traceId={} slotId={} mysqlAvailable={} bookedUserCount={} costMs={}",
                TraceContext.traceId(),
                slotId,
                slot.availableCount(),
                bookedUserIds.size(),
                System.currentTimeMillis() - start
        );
    }

    public void resetSlotForDev(Long slotId) {
        long start = System.currentTimeMillis();
        bookingRecordRepository.deleteBySlot(slotId);
        resourceSlotRepository.resetAvailableToTotal(slotId);
        warmupSlot(slotId);
        log.warn(
                "event=slot.dev.reset traceId={} slotId={} costMs={}",
                TraceContext.traceId(),
                slotId,
                System.currentTimeMillis() - start
        );
    }
}
