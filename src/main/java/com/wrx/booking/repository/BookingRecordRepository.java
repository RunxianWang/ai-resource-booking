package com.wrx.booking.repository;

import com.wrx.booking.domain.BookingRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class BookingRecordRepository {

    public static final String STATUS_RESERVED = "RESERVED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_FINISHED = "FINISHED";

    private final JdbcTemplate jdbcTemplate;

    public BookingRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long insertReserved(Long userId, Long slotId, Long machineId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO booking_record(user_id, slot_id, machine_id, status)
                    VALUES (?, ?, ?, 'RESERVED')
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, userId);
            ps.setLong(2, slotId);
            ps.setLong(3, machineId);
            return ps;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public boolean existsByUserAndSlot(Long userId, Long slotId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM booking_record
                WHERE user_id = ?
                  AND slot_id = ?
                """,
                Integer.class,
                userId,
                slotId
        );
        return count != null && count > 0;
    }

    public Optional<BookingRecord> findById(Long bookingId) {
        List<BookingRecord> list = jdbcTemplate.query(
                """
                SELECT id, user_id, slot_id, machine_id, status, created_at
                FROM booking_record
                WHERE id = ?
                """,
                (rs, rowNum) -> new BookingRecord(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getLong("slot_id"),
                        rs.getLong("machine_id"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ),
                bookingId
        );
        return list.stream().findFirst();
    }

    public int cancelReserved(Long bookingId, Long userId) {
        return jdbcTemplate.update(
                """
                UPDATE booking_record b
                INNER JOIN resource_slot s ON s.id = b.slot_id
                SET b.status = 'CANCELLED'
                WHERE b.id = ?
                  AND b.user_id = ?
                  AND b.status = 'RESERVED'
                  AND s.end_time > NOW()
                """,
                bookingId,
                userId
        );
    }

    public int finishExpiredReservedBookings() {
        return jdbcTemplate.update(
                """
                UPDATE booking_record b
                INNER JOIN resource_slot s ON s.id = b.slot_id
                SET b.status = 'FINISHED'
                WHERE b.status = 'RESERVED'
                  AND s.end_time <= NOW()
                """
        );
    }

    public List<Long> findReservedUserIdsBySlot(Long slotId) {
        return jdbcTemplate.queryForList(
                """
                SELECT user_id
                FROM booking_record
                WHERE slot_id = ?
                  AND status = 'RESERVED'
                """,
                Long.class,
                slotId
        );
    }

    public int countSuccessBySlot(Long slotId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM booking_record
                WHERE slot_id = ?
                  AND status = 'RESERVED'
                """,
                Integer.class,
                slotId
        );
        return count == null ? 0 : count;
    }

    public int countBySlot(Long slotId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM booking_record
                WHERE slot_id = ?
                """,
                Integer.class,
                slotId
        );
        return count == null ? 0 : count;
    }

    public int countCanceledBySlot(Long slotId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM booking_record
                WHERE slot_id = ?
                  AND status IN ('CANCELLED', 'CANCELED')
                """,
                Integer.class,
                slotId
        );
        return count == null ? 0 : count;
    }

    public List<BookingRecord> findLatestBySlot(Long slotId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, user_id, slot_id, machine_id, status, created_at
                FROM booking_record
                WHERE slot_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new BookingRecord(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getLong("slot_id"),
                        rs.getLong("machine_id"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ),
                slotId,
                limit
        );
    }

    public List<BookingRecord> findByUserId(Long userId) {
        return jdbcTemplate.query(
                """
                SELECT id, user_id, slot_id, machine_id, status, created_at
                FROM booking_record
                WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                (rs, rowNum) -> new BookingRecord(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getLong("slot_id"),
                        rs.getLong("machine_id"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ),
                userId
        );
    }

    public int deleteBySlot(Long slotId) {
        return jdbcTemplate.update(
                """
                DELETE FROM booking_record
                WHERE slot_id = ?
                """,
                slotId
        );
    }
}
