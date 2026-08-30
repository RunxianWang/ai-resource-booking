package com.wrx.booking.repository;

import com.wrx.booking.domain.BookingSuccessEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public BookingEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertProjection(BookingSuccessEvent event, String bookingStatus) {
        return jdbcTemplate.update(
                """
                INSERT INTO booking_event_projection
                    (booking_id, user_id, slot_id, event_type, booking_status, last_message_key, event_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    user_id = VALUES(user_id),
                    slot_id = VALUES(slot_id),
                    event_type = VALUES(event_type),
                    booking_status = VALUES(booking_status),
                    last_message_key = VALUES(last_message_key),
                    event_time = VALUES(event_time)
                """,
                event.bookingId(),
                event.userId(),
                event.slotId(),
                event.eventType(),
                bookingStatus,
                event.messageKey(),
                event.createdAt()
        );
    }

    public int insertAudit(BookingSuccessEvent event, String consumerGroup) {
        return jdbcTemplate.update(
                """
                INSERT IGNORE INTO booking_event_audit
                    (booking_id, message_key, consumer_group, event_type, processing_status)
                VALUES (?, ?, ?, ?, 'SUCCESS')
                """,
                event.bookingId(),
                event.messageKey(),
                consumerGroup,
                event.eventType()
        );
    }
}
