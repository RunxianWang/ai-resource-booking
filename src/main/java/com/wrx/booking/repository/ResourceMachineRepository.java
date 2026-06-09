package com.wrx.booking.repository;

import com.wrx.booking.domain.ResourceMachine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ResourceMachineRepository {

    private final JdbcTemplate jdbcTemplate;

    public ResourceMachineRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ResourceMachine> findActiveMachines() {
        return jdbcTemplate.query(
                """
                SELECT id, machine_name, resource_type, gpu_model, status, created_at, updated_at
                FROM resource_machine
                WHERE status = 'ACTIVE'
                ORDER BY id ASC
                """,
                (rs, rowNum) -> new ResourceMachine(
                        rs.getLong("id"),
                        rs.getString("machine_name"),
                        rs.getString("resource_type"),
                        rs.getString("gpu_model"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime()
                )
        );
    }
}
