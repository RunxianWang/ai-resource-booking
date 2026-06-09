package com.wrx.booking.repository;

import com.wrx.booking.domain.MessageLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageLogRepository {

    public static final String STATUS_INIT = "INIT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SENT = "SENT";

    private final JdbcTemplate jdbcTemplate;

    public MessageLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertInit(Long bookingId, String messageKey, String topic, String eventType, String payload) {
        return jdbcTemplate.update(
                """
                INSERT INTO message_log(booking_id, message_key, topic, event_type, payload, status, retry_count)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """,
                bookingId,
                messageKey,
                topic,
                eventType,
                payload,
                STATUS_INIT
        );
    }

    public List<MessageLog> findPending(int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, booking_id, message_key, topic, event_type, payload, status, retry_count, last_error, created_at, updated_at
                FROM message_log
                WHERE status IN (?, ?)
                  AND message_key IS NOT NULL
                  AND topic IS NOT NULL
                  AND payload IS NOT NULL
                ORDER BY created_at ASC, id ASC
                LIMIT ?
                """,
                (rs, rowNum) -> new MessageLog(
                        rs.getLong("id"),
                        rs.getLong("booking_id"),
                        rs.getString("message_key"),
                        rs.getString("topic"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getString("status"),
                        rs.getInt("retry_count"),
                        rs.getString("last_error"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime()
                ),
                STATUS_INIT,
                STATUS_FAILED,
                limit
        );
    }

    public int markSent(Long id) {
        return jdbcTemplate.update(
                """
                UPDATE message_log
                SET status = ?, last_error = NULL
                WHERE id = ?
                """,
                STATUS_SENT,
                id
        );
    }

    public int markFailed(Long id, String lastError) {
        return jdbcTemplate.update(
                """
                UPDATE message_log
                SET status = ?, retry_count = retry_count + 1, last_error = ?
                WHERE id = ?
                """,
                STATUS_FAILED,
                lastError,
                id
        );
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_log",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    public int countSent() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_log WHERE status = ?",
                Integer.class,
                STATUS_SENT
        );
        return count == null ? 0 : count;
    }

    public int countBySlot(Long slotId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM message_log
                WHERE JSON_VALID(payload) = 1
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.slotId')) = ?
                """,
                Integer.class,
                String.valueOf(slotId)
        );
        return count == null ? 0 : count;
    }

    public int countSentBySlot(Long slotId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM message_log
                WHERE status = ?
                  AND JSON_VALID(payload) = 1
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.slotId')) = ?
                """,
                Integer.class,
                STATUS_SENT,
                String.valueOf(slotId)
        );
        return count == null ? 0 : count;
    }

    public int countBookingSuccessBySlot(Long slotId) {
        return countBookingSuccessBySlotAndStatus(slotId, null);
    }

    public int countBySlotAndEventType(Long slotId, String eventType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM message_log m
                INNER JOIN booking_record b ON b.id = m.booking_id
                WHERE b.slot_id = ?
                  AND m.event_type = ?
                """,
                Integer.class,
                slotId,
                eventType
        );
        return count == null ? 0 : count;
    }

    public int countBySlotAndStatus(Long slotId, String status) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM message_log m
                INNER JOIN booking_record b ON b.id = m.booking_id
                WHERE b.slot_id = ?
                  AND m.status = ?
                """,
                Integer.class,
                slotId,
                status
        );
        return count == null ? 0 : count;
    }

    public int countBookingSuccessBySlotAndStatus(Long slotId, String status) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM message_log m
                INNER JOIN booking_record b ON b.id = m.booking_id
                WHERE b.slot_id = ?
                  AND m.event_type = 'BOOKING_RESERVED'
                  AND (? IS NULL OR m.status = ?)
                """,
                Integer.class,
                slotId,
                status,
                status
        );
        return count == null ? 0 : count;
    }

    public List<MessageLog> findLatestBySlot(Long slotId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT m.id, m.booking_id, m.message_key, m.topic, m.event_type, m.payload,
                       m.status, m.retry_count, m.last_error, m.created_at, m.updated_at
                FROM message_log m
                INNER JOIN booking_record b ON b.id = m.booking_id
                WHERE b.slot_id = ?
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new MessageLog(
                        rs.getLong("id"),
                        rs.getLong("booking_id"),
                        rs.getString("message_key"),
                        rs.getString("topic"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getString("status"),
                        rs.getInt("retry_count"),
                        rs.getString("last_error"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime()
                ),
                slotId,
                limit
        );
    }

    public List<MessageLog> findLatestBookingSuccessBySlot(Long slotId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT m.id, m.booking_id, m.message_key, m.topic, m.event_type, m.payload,
                       m.status, m.retry_count, m.last_error, m.created_at, m.updated_at
                FROM message_log m
                INNER JOIN booking_record b ON b.id = m.booking_id
                WHERE b.slot_id = ?
                  AND m.event_type = 'BOOKING_RESERVED'
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new MessageLog(
                        rs.getLong("id"),
                        rs.getLong("booking_id"),
                        rs.getString("message_key"),
                        rs.getString("topic"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getString("status"),
                        rs.getInt("retry_count"),
                        rs.getString("last_error"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime()
                ),
                slotId,
                limit
        );
    }
}
