package com.wrx.booking.repository;

import com.wrx.booking.domain.ConsumeLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ConsumeLogRepository {

    public static final String STATUS_SUCCESS = "SUCCESS";

    private final JdbcTemplate jdbcTemplate;

    public ConsumeLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertSuccess(String messageKey, String consumerGroup) {
        return jdbcTemplate.update(
                """
                INSERT IGNORE INTO consume_log(message_key, consumer_group, status)
                VALUES (?, ?, ?)
                """,
                messageKey,
                consumerGroup,
                STATUS_SUCCESS
        );
    }

    public int countBySlot(Long slotId, String consumerGroup) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM consume_log c
                INNER JOIN message_log m ON m.message_key = c.message_key
                WHERE c.consumer_group = ?
                  AND JSON_VALID(m.payload) = 1
                  AND JSON_UNQUOTE(JSON_EXTRACT(m.payload, '$.slotId')) = ?
                """,
                Integer.class,
                consumerGroup,
                String.valueOf(slotId)
        );
        return count == null ? 0 : count;
    }

    public int countSuccessBySlot(Long slotId, String consumerGroup) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM consume_log c
                INNER JOIN message_log m ON m.message_key = c.message_key
                INNER JOIN booking_record b ON b.id = m.booking_id
                WHERE c.consumer_group = ?
                  AND c.status = ?
                  AND m.event_type = 'BOOKING_RESERVED'
                  AND b.slot_id = ?
                """,
                Integer.class,
                consumerGroup,
                STATUS_SUCCESS,
                slotId
        );
        return count == null ? 0 : count;
    }

    public List<ConsumeLog> findLatestBySlot(Long slotId, String consumerGroup, int limit) {
        return jdbcTemplate.query(
                """
                SELECT c.id, c.message_key, c.consumer_group, c.status, c.created_at, c.updated_at
                FROM consume_log c
                INNER JOIN message_log m ON m.message_key = c.message_key
                INNER JOIN booking_record b ON b.id = m.booking_id
                WHERE c.consumer_group = ?
                  AND b.slot_id = ?
                ORDER BY c.created_at DESC, c.id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new ConsumeLog(
                        rs.getLong("id"),
                        rs.getString("message_key"),
                        rs.getString("consumer_group"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime()
                ),
                consumerGroup,
                slotId,
                limit
        );
    }
}
