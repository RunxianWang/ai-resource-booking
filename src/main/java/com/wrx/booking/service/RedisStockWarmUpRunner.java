package com.wrx.booking.service;

import com.wrx.booking.domain.ResourceSlotCatalog;
import com.wrx.booking.repository.BookingRecordRepository;
import com.wrx.booking.repository.ResourceSlotRepository;
import com.wrx.booking.support.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisStockWarmUpRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisStockWarmUpRunner.class);

    private final ResourceSlotRepository resourceSlotRepository;
    private final StringRedisTemplate redisTemplate;
    private final BookingRecordRepository bookingRecordRepository;

    public RedisStockWarmUpRunner(
            ResourceSlotRepository resourceSlotRepository,
            StringRedisTemplate redisTemplate,
            BookingRecordRepository bookingRecordRepository
    ) {
        this.resourceSlotRepository = resourceSlotRepository;
        this.redisTemplate = redisTemplate;
        this.bookingRecordRepository = bookingRecordRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("event=redis.stock.warmup.start");

        int scannedCount = 0;
        int initializedCount = 0;
        int skippedCount = 0;

        try {
            List<ResourceSlotCatalog> slots = resourceSlotRepository.findAllForWarmup();
            scannedCount = slots.size();

            if (slots.isEmpty()) {
                log.warn("event=redis.stock.warmup.empty reason=no_resource_slots");
            } else {
                for (ResourceSlotCatalog slot : slots) {
                    WarmUpResult result = warmUpSlot(slot);
                    if (result == WarmUpResult.INITIALIZED) {
                        initializedCount++;
                    } else if (result == WarmUpResult.SKIPPED) {
                        skippedCount++;
                    }
                }
            }
        } catch (Exception e) {
            log.error(
                    "event=redis.stock.warmup.failed scannedCount={} initializedCount={} skippedCount={} reason={}",
                    scannedCount,
                    initializedCount,
                    skippedCount,
                    e.getMessage(),
                    e
            );
            return;
        }

        log.info(
                "event=redis.stock.warmup.completed scannedCount={} initializedCount={} skippedCount={}",
                scannedCount,
                initializedCount,
                skippedCount
        );
    }

    private WarmUpResult warmUpSlot(ResourceSlotCatalog slot) {
        try {
            if (slot.id() == null || slot.availableCount() == null || slot.availableCount() < 0) {
                log.error(
                        "event=redis.stock.warmup.slot.invalid slotId={} availableCount={} reason=invalid_slot_data",
                        slot.id(),
                        slot.availableCount()
                );
                return WarmUpResult.ERROR;
            }

            String availableKey = RedisKeys.slotAvailable(slot.id());
            redisTemplate.delete(availableKey);
            redisTemplate.delete(RedisKeys.slotBookedUsers(slot.id()));
            redisTemplate.opsForValue().set(availableKey, String.valueOf(slot.availableCount()));
            List<Long> bookedUserIds = bookingRecordRepository.findReservedUserIdsBySlot(slot.id());
            if (!bookedUserIds.isEmpty()) {
                redisTemplate.opsForSet().add(
                        RedisKeys.slotBookedUsers(slot.id()),
                        bookedUserIds.stream().map(String::valueOf).toArray(String[]::new)
                );
            }
            log.info(
                    "event=redis.stock.warmup.slot.init slotId={} key={} availableCount={} bookedUserCount={}",
                    slot.id(),
                    availableKey,
                    slot.availableCount(),
                    bookedUserIds.size()
            );
            return WarmUpResult.INITIALIZED;
        } catch (Exception e) {
            log.error(
                    "event=redis.stock.warmup.slot.error slotId={} reason={}",
                    slot == null ? null : slot.id(),
                    e.getMessage(),
                    e
            );
            return WarmUpResult.ERROR;
        }
    }

    private enum WarmUpResult {
        INITIALIZED,
        SKIPPED,
        ERROR
    }
}
