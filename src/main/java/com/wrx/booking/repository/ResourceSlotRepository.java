package com.wrx.booking.repository;

import com.wrx.booking.domain.ResourceMachine;
import com.wrx.booking.domain.ResourceSlot;
import com.wrx.booking.domain.ResourceSlotCatalog;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ResourceSlotRepository {

    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_RESERVED = "RESERVED";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final JdbcTemplate jdbcTemplate;

    public ResourceSlotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ResourceSlot> findById(Long slotId) {
        List<ResourceSlot> list = jdbcTemplate.query(
                """
                SELECT id, machine_id, resource_name, resource_type, start_time, end_time,
                       total_count, available_count, status
                FROM resource_slot
                WHERE id = ?
                """,
                (rs, rowNum) -> new ResourceSlot(
                        rs.getLong("id"),
                        rs.getLong("machine_id"),
                        rs.getString("resource_name"),
                        rs.getString("resource_type"),
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getTimestamp("end_time").toLocalDateTime(),
                        rs.getInt("total_count"),
                        rs.getInt("available_count"),
                        rs.getString("status")
                ),
                slotId
        );

        return list.stream().findFirst();
    }

    public Optional<ResourceSlot> findByMachineAndWindow(Long machineId, LocalDateTime startTime, LocalDateTime endTime) {
        List<ResourceSlot> list = jdbcTemplate.query(
                """
                SELECT id, machine_id, resource_name, resource_type, start_time, end_time,
                       total_count, available_count, status
                FROM resource_slot
                WHERE machine_id = ?
                  AND start_time = ?
                  AND end_time = ?
                """,
                (rs, rowNum) -> new ResourceSlot(
                        rs.getLong("id"),
                        rs.getLong("machine_id"),
                        rs.getString("resource_name"),
                        rs.getString("resource_type"),
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getTimestamp("end_time").toLocalDateTime(),
                        rs.getInt("total_count"),
                        rs.getInt("available_count"),
                        rs.getString("status")
                ),
                machineId,
                startTime,
                endTime
        );
        return list.stream().findFirst();
    }

    public Optional<ResourceSlot> findByMachineAndStartTime(Long machineId, LocalDateTime startTime) {
        List<ResourceSlot> list = jdbcTemplate.query(
                """
                SELECT id, machine_id, resource_name, resource_type, start_time, end_time,
                       total_count, available_count, status
                FROM resource_slot
                WHERE machine_id = ? AND start_time = ?
                """,
                (rs, rowNum) -> new ResourceSlot(
                        rs.getLong("id"), rs.getLong("machine_id"), rs.getString("resource_name"),
                        rs.getString("resource_type"), rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getTimestamp("end_time").toLocalDateTime(), rs.getInt("total_count"),
                        rs.getInt("available_count"), rs.getString("status")
                ), machineId, startTime);
        return list.stream().findFirst();
    }

    public List<ResourceSlotCatalog> findAllForCatalog() {
        return jdbcTemplate.query(
                """
                SELECT resource_slot.id, resource_slot.machine_id, resource_slot.resource_name, resource_slot.resource_type,
                       resource_slot.start_time, resource_slot.end_time, resource_slot.total_count,
                       resource_slot.available_count, resource_slot.status
                FROM resource_slot
                JOIN resource_machine m ON m.id = resource_slot.machine_id AND m.status = 'ACTIVE'
                WHERE DATE(resource_slot.start_time) = CURRENT_DATE
                  AND resource_slot.start_time >= DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00') + INTERVAL 1 HOUR
                  AND resource_slot.end_time <= DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY)
                  AND resource_slot.end_time > NOW()
                  AND resource_slot.status <> 'FINISHED'
                ORDER BY resource_slot.machine_id ASC, resource_slot.start_time ASC, resource_slot.id ASC
                """,
                (rs, rowNum) -> new ResourceSlotCatalog(
                        rs.getLong("id"),
                        rs.getLong("machine_id"),
                        rs.getString("resource_name"),
                        rs.getString("resource_type"),
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getTimestamp("end_time").toLocalDateTime(),
                        rs.getInt("total_count"),
                        rs.getInt("available_count"),
                        rs.getString("status")
                )
        );
    }

    public List<ResourceSlotCatalog> findAllForWarmup() {
        return jdbcTemplate.query(
                """
                SELECT resource_slot.id, resource_slot.machine_id, resource_slot.resource_name, resource_slot.resource_type,
                       resource_slot.start_time, resource_slot.end_time, resource_slot.total_count,
                       resource_slot.available_count, resource_slot.status
                FROM resource_slot
                JOIN resource_machine m ON m.id = resource_slot.machine_id AND m.status = 'ACTIVE'
                WHERE DATE(resource_slot.start_time) = CURRENT_DATE
                  AND resource_slot.start_time >= DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00') + INTERVAL 1 HOUR
                  AND resource_slot.end_time <= DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY)
                  AND resource_slot.end_time > NOW()
                  AND resource_slot.status <> 'FINISHED'
                ORDER BY resource_slot.machine_id ASC, resource_slot.start_time ASC, resource_slot.id ASC
                """,
                (rs, rowNum) -> new ResourceSlotCatalog(
                        rs.getLong("id"),
                        rs.getLong("machine_id"),
                        rs.getString("resource_name"),
                        rs.getString("resource_type"),
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getTimestamp("end_time").toLocalDateTime(),
                        rs.getInt("total_count"),
                        rs.getInt("available_count"),
                        rs.getString("status")
                )
        );
    }

    public int reserveAvailableSlot(Long slotId) {
        return jdbcTemplate.update(
                """
                UPDATE resource_slot
                SET available_count = available_count - 1
                WHERE id = ?
                  AND available_count > 0
                  AND end_time > NOW()
                """,
                slotId
        );
    }

    public int releaseReservedSlot(Long slotId) {
        return jdbcTemplate.update(
                """
                UPDATE resource_slot
                SET available_count = LEAST(available_count + 1, total_count)
                WHERE id = ?
                  AND available_count < total_count
                """,
                slotId
        );
    }

    public int finishExpiredReservedSlots() {
        return jdbcTemplate.update(
                """
                UPDATE resource_slot
                SET available_count = 0,
                    status = 'FINISHED'
                WHERE status = 'RESERVED'
                  AND end_time <= NOW()
                """
        );
    }

    public boolean insertSlotIgnoreDuplicate(ResourceMachine machine, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            int affected = jdbcTemplate.update(
                    """
                    INSERT INTO resource_slot(
                        machine_id, resource_id, resource_name, resource_type,
                        start_time, end_time, total_count, available_count, status
                    )
                    VALUES (?, ?, ?, ?, ?, ?, 1, 1, 'AVAILABLE')
                    """,
                    machine.id(),
                    machine.id(),
                    machine.machineName(),
                    machine.resourceType(),
                    startTime,
                    endTime
            );
            return affected > 0;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public int resetAvailableToTotal(Long slotId) {
        return jdbcTemplate.update(
                """
                UPDATE resource_slot
                SET available_count = total_count,
                    status = 'AVAILABLE'
                WHERE id = ?
                  AND end_time > NOW()
                """,
                slotId
        );
    }
}
