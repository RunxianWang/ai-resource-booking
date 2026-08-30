package com.wrx.booking.repository;

import com.wrx.booking.domain.DeadLetterLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DeadLetterLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public DeadLetterLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertIgnore(DeadLetterLog log) {
        return jdbcTemplate.update(
                """
                INSERT IGNORE INTO dead_letter_log
                    (message_key, consumer_group, original_topic, original_partition, original_offset,
                     payload, exception_class, exception_message, status, retry_count, replay_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """,
                log.messageKey(), log.consumerGroup(), log.originalTopic(), log.originalPartition(),
                log.originalOffset(), log.payload(), log.exceptionClass(), log.exceptionMessage(),
                log.retryCount(), log.replayCount()
        );
    }

    public List<DeadLetterLog> findAll(int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, message_key, consumer_group, original_topic, original_partition, original_offset,
                       payload, exception_class, exception_message, status, retry_count, replay_count,
                       created_at, replayed_at
                FROM dead_letter_log
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new DeadLetterLog(
                        rs.getLong("id"), rs.getString("message_key"), rs.getString("consumer_group"),
                        rs.getString("original_topic"), (Integer) rs.getObject("original_partition"),
                        (Long) rs.getObject("original_offset"), rs.getString("payload"),
                        rs.getString("exception_class"), rs.getString("exception_message"),
                        rs.getString("status"), rs.getInt("retry_count"), rs.getInt("replay_count"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("replayed_at") == null ? null : rs.getTimestamp("replayed_at").toLocalDateTime()
                ), limit
        );
    }

    public DeadLetterLog findById(long id) {
        return findAll(1000).stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("dead letter not found: " + id));
    }

    public int markReplayed(long id) {
        return jdbcTemplate.update(
                "UPDATE dead_letter_log SET status = 'REPLAYED', replay_count = replay_count + 1, replayed_at = NOW() WHERE id = ? AND status = 'PENDING'",
                id
        );
    }

    public int markIgnored(long id) {
        return jdbcTemplate.update("UPDATE dead_letter_log SET status = 'IGNORED' WHERE id = ? AND status = 'PENDING'", id);
    }
}
