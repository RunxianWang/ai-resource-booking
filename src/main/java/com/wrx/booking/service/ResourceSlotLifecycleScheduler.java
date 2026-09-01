package com.wrx.booking.service;

import com.wrx.booking.domain.ResourceMachine;
import com.wrx.booking.domain.ResourceSlot;
import com.wrx.booking.repository.BookingRecordRepository;
import com.wrx.booking.repository.ResourceMachineRepository;
import com.wrx.booking.repository.ResourceSlotRepository;
import com.wrx.booking.support.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class ResourceSlotLifecycleScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ResourceSlotLifecycleScheduler.class);
    private static final int FUTURE_SLOT_HOURS = 23;

    private final ResourceMachineRepository resourceMachineRepository;
    private final ResourceSlotRepository resourceSlotRepository;
    private final BookingRecordRepository bookingRecordRepository;
    private final StringRedisTemplate redisTemplate;

    public ResourceSlotLifecycleScheduler(
            ResourceMachineRepository resourceMachineRepository,
            ResourceSlotRepository resourceSlotRepository,
            BookingRecordRepository bookingRecordRepository,
            StringRedisTemplate redisTemplate
    ) {
        this.resourceMachineRepository = resourceMachineRepository;
        this.resourceSlotRepository = resourceSlotRepository;
        this.bookingRecordRepository = bookingRecordRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        generateFutureSlots();
        finishExpiredReservations();
    }

    @Scheduled(fixedDelayString = "${booking.slot.lifecycle.generate-fixed-delay-ms:1800000}")
    public void generateFutureSlots() {
        LocalDateTime firstStart = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(1);
        LocalDateTime endOfToday = LocalDateTime.now().toLocalDate().plusDays(1).atStartOfDay();
        List<ResourceMachine> machines = resourceMachineRepository.findActiveMachines();

        int createdCount = 0;
        int existingCount = 0;
        int redisInitializedCount = 0;
        int redisSkippedCount = 0;

        for (ResourceMachine machine : machines) {
            for (int i = 0; i < FUTURE_SLOT_HOURS; i++) {
                LocalDateTime startTime = firstStart.plusHours(i);
                LocalDateTime endTime = startTime.plusHours(1);
                if (!startTime.isBefore(endOfToday)) break;

                boolean created = resourceSlotRepository.insertSlotIgnoreDuplicate(machine, startTime, endTime);
                if (created) {
                    createdCount++;
                } else {
                    existingCount++;
                }

                ResourceSlot slot = resourceSlotRepository.findByMachineAndWindow(machine.id(), startTime, endTime)
                        .orElse(null);
                if (slot == null) {
                    continue;
                }

                String availableKey = RedisKeys.slotAvailable(slot.id());
                if (Boolean.TRUE.equals(redisTemplate.hasKey(availableKey))) {
                    redisSkippedCount++;
                    continue;
                }
                redisTemplate.opsForValue().set(availableKey, String.valueOf(slot.availableCount()));
                redisInitializedCount++;
            }
        }

        log.info(
                "event=slot.lifecycle.generate machines={} createdCount={} existingCount={} redisInitializedCount={} redisSkippedCount={} firstStart={} hours={}",
                machines.size(),
                createdCount,
                existingCount,
                redisInitializedCount,
                redisSkippedCount,
                firstStart,
                FUTURE_SLOT_HOURS
        );
    }

    @Transactional
    @Scheduled(fixedDelayString = "${booking.slot.lifecycle.finish-fixed-delay-ms:300000}")
    public void finishExpiredReservations() {
        int finishedBookingCount = bookingRecordRepository.finishExpiredReservedBookings();
        int finishedSlotCount = resourceSlotRepository.finishExpiredReservedSlots();
        if (finishedBookingCount > 0 || finishedSlotCount > 0) {
            log.info(
                    "event=slot.lifecycle.finish finishedBookingCount={} finishedSlotCount={}",
                    finishedBookingCount,
                    finishedSlotCount
            );
        }
    }
}
