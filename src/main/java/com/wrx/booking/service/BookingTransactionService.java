package com.wrx.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrx.booking.api.dto.BookingCancelResponse;
import com.wrx.booking.api.dto.BookingResponse;
import com.wrx.booking.config.KafkaConfig;
import com.wrx.booking.domain.BookingCanceledEvent;
import com.wrx.booking.domain.BookingRecord;
import com.wrx.booking.domain.BookingSuccessEvent;
import com.wrx.booking.domain.ResourceSlot;
import com.wrx.booking.repository.BookingRecordRepository;
import com.wrx.booking.repository.MessageLogRepository;
import com.wrx.booking.repository.ResourceSlotRepository;
import com.wrx.booking.support.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookingTransactionService {
    private static final String RESERVED_EVENT = "BOOKING_RESERVED";
    private static final String CANCELLED_EVENT = "BOOKING_CANCELLED";

    private final ResourceSlotRepository slotRepository;
    private final BookingRecordRepository bookingRepository;
    private final MessageLogRepository messageLogRepository;
    private final ObjectMapper objectMapper;

    public BookingTransactionService(ResourceSlotRepository slotRepository, BookingRecordRepository bookingRepository,
                                     MessageLogRepository messageLogRepository, ObjectMapper objectMapper) {
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.messageLogRepository = messageLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BookingResponse createBooking(Long userId, Long slotId) {
        ResourceSlot slot = slotRepository.findById(slotId).orElse(null);
        if (slot == null) return BookingResponse.fail(ErrorCode.RESOURCE_NOT_FOUND, "slot not found: " + slotId, userId, slotId);
        if (slotRepository.reserveAvailableSlot(slotId) != 1) {
            return BookingResponse.fail(ErrorCode.SOLD_OUT, "MySQL slot is not available or already expired", userId, slotId);
        }
        Long bookingId = bookingRepository.insertReserved(userId, slotId, slot.machineId());
        String key = "booking:" + bookingId + ":reserved";
        BookingSuccessEvent event = new BookingSuccessEvent(key, bookingId, userId, slotId, slot.machineId(),
                slot.resourceName(), slot.startTime().toString(), slot.endTime().toString(), RESERVED_EVENT, Instant.now().toString());
        messageLogRepository.insertInit(bookingId, key, KafkaConfig.BOOKING_SUCCESS_TOPIC, RESERVED_EVENT, json(event));
        return BookingResponse.success(bookingId, userId, slotId);
    }

    @Transactional
    public BookingResponse createBooking(Long userId, List<ResourceSlot> slots) {
        List<Long> bookingIds = new ArrayList<>();
        for (ResourceSlot slot : slots) {
            if (slotRepository.reserveAvailableSlot(slot.id()) != 1) {
                // Throw so the surrounding transaction rolls back any earlier slot updates.
                throw new IllegalStateException("one of the requested slots became unavailable");
            }
            Long bookingId = bookingRepository.insertReserved(userId, slot.id(), slot.machineId());
            bookingIds.add(bookingId);
            String key = "booking:" + bookingId + ":reserved";
            BookingSuccessEvent event = new BookingSuccessEvent(key, bookingId, userId, slot.id(), slot.machineId(),
                    slot.resourceName(), slot.startTime().toString(), slot.endTime().toString(), RESERVED_EVENT, Instant.now().toString());
            messageLogRepository.insertInit(bookingId, key, KafkaConfig.BOOKING_SUCCESS_TOPIC, RESERVED_EVENT, json(event));
        }
        return BookingResponse.success(bookingIds, userId, slots.stream().map(ResourceSlot::id).toList());
    }

    @Transactional
    public BookingCancelResponse cancelBooking(Long bookingId, Long currentUserId) {
        BookingRecord booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) return BookingCancelResponse.fail("BOOKING_NOT_FOUND", "预约记录不存在", "booking not found: " + bookingId, bookingId, null, null);
        if (!currentUserId.equals(booking.userId())) {
            return BookingCancelResponse.fail("BOOKING_FORBIDDEN", "不能取消其他用户的预约", "booking owner does not match current user", bookingId, currentUserId, booking.slotId());
        }
        if (bookingRepository.cancelReserved(bookingId, currentUserId) != 1) {
            return BookingCancelResponse.fail("BOOKING_CANCEL_SKIPPED", "预约不可取消、已取消或已结束", "only unexpired RESERVED booking can be canceled", bookingId, currentUserId, booking.slotId());
        }
        slotRepository.releaseReservedSlot(booking.slotId());
        ResourceSlot slot = slotRepository.findById(booking.slotId()).orElse(null);
        String key = "booking:" + bookingId + ":cancelled";
        BookingCanceledEvent event = new BookingCanceledEvent(key, bookingId, booking.userId(), booking.slotId(),
                slot == null ? booking.machineId() : slot.machineId(), slot == null ? null : slot.resourceName(),
                slot == null ? null : slot.startTime().toString(), slot == null ? null : slot.endTime().toString(), CANCELLED_EVENT, Instant.now().toString());
        messageLogRepository.insertInit(bookingId, key, KafkaConfig.BOOKING_SUCCESS_TOPIC, CANCELLED_EVENT, json(event));
        return BookingCancelResponse.success(bookingId, booking.userId(), booking.slotId());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serialize booking event failed", e);
        }
    }
}
