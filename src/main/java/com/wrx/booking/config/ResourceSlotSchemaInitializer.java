package com.wrx.booking.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ResourceSlotSchemaInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ResourceSlotSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public ResourceSlotSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        ensureResourceMachineTable();
        seedDemoMachines();

        if (!tableExists("resource_slot")) {
            return;
        }

        ensureColumn("machine_id", "ALTER TABLE resource_slot ADD COLUMN machine_id BIGINT NULL AFTER id");
        ensureColumn("resource_name", "ALTER TABLE resource_slot ADD COLUMN resource_name VARCHAR(128) NULL AFTER resource_id");
        ensureColumn("resource_type", "ALTER TABLE resource_slot ADD COLUMN resource_type VARCHAR(32) NULL AFTER resource_name");

        jdbcTemplate.update(
                """
                UPDATE resource_slot
                SET machine_id = CASE
                        WHEN id = 1 THEN 3
                        WHEN id = 2 THEN 1
                        WHEN id = 3 THEN 2
                        ELSE COALESCE(machine_id, 1)
                    END,
                    resource_name = CASE
                        WHEN resource_name IS NOT NULL THEN resource_name
                        WHEN id = 1 THEN 'A100-Node-01'
                        WHEN id = 2 THEN 'H100-Node-01'
                        WHEN id = 3 THEN 'H100-Node-02'
                        ELSE CONCAT('GPU Machine ', COALESCE(machine_id, resource_id, id))
                    END,
                    resource_type = COALESCE(resource_type, 'GPU'),
                    status = CASE
                        WHEN status IN ('SUCCESS', 'RESERVED') THEN 'RESERVED'
                        WHEN status IN ('CANCELED', 'CANCELLED') THEN 'CANCELLED'
                        WHEN end_time <= NOW() THEN 'FINISHED'
                        ELSE 'AVAILABLE'
                    END
                WHERE machine_id IS NULL
                   OR resource_name IS NULL
                   OR resource_type IS NULL
                   OR status IN ('OPEN', 'SUCCESS', 'CANCELED')
                """
        );

        ensureIndex("idx_slot_status_end_time", "ALTER TABLE resource_slot ADD INDEX idx_slot_status_end_time (status, end_time)");
        ensureIndex("idx_resource_time", "ALTER TABLE resource_slot ADD INDEX idx_resource_time (machine_id, start_time, end_time)");
        ensureIndex("uk_machine_time", "ALTER TABLE resource_slot ADD UNIQUE KEY uk_machine_time (machine_id, start_time, end_time)");
    }

    private void ensureResourceMachineTable() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS resource_machine (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    machine_name VARCHAR(128) NOT NULL,
                    resource_type VARCHAR(32) NOT NULL,
                    gpu_model VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_machine_name (machine_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
        );
    }

    private void seedDemoMachines() {
        jdbcTemplate.update(
                """
                INSERT IGNORE INTO resource_machine(id, machine_name, resource_type, gpu_model, status)
                VALUES
                    (1, 'H100-Node-01', 'GPU', 'H100', 'ACTIVE'),
                    (2, 'H100-Node-02', 'GPU', 'H100', 'ACTIVE'),
                    (3, 'A100-Node-01', 'GPU', 'A100', 'ACTIVE')
                """
        );
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """,
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private void ensureColumn(String columnName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'resource_slot'
                  AND column_name = ?
                """,
                Integer.class,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(alterSql);
        }
    }

    private void ensureIndex(String indexName, String alterSql) {
        if (indexExists(indexName)) {
            return;
        }
        try {
            jdbcTemplate.execute(alterSql);
        } catch (Exception e) {
            log.warn("event=schema.resource_slot.index.skip indexName={} reason={}", indexName, e.getMessage());
        }
    }

    private boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'resource_slot'
                  AND index_name = ?
                """,
                Integer.class,
                indexName
        );
        return count != null && count > 0;
    }
}
