package com.wrx.booking.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@DependsOn("resourceSlotSchemaInitializer")
public class BookingRecordSchemaInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(BookingRecordSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public BookingRecordSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        if (!tableExists("booking_record")) {
            return;
        }

        ensureColumn("machine_id", "ALTER TABLE booking_record ADD COLUMN machine_id BIGINT NULL AFTER slot_id");
        ensureColumn("updated_at", "ALTER TABLE booking_record ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at");

        jdbcTemplate.update(
                """
                UPDATE booking_record b
                INNER JOIN resource_slot s ON s.id = b.slot_id
                SET b.machine_id = COALESCE(b.machine_id, s.machine_id),
                    b.status = CASE
                        WHEN b.status = 'SUCCESS' THEN 'RESERVED'
                        WHEN b.status = 'CANCELED' THEN 'CANCELLED'
                        ELSE b.status
                    END
                WHERE b.machine_id IS NULL
                   OR b.status IN ('SUCCESS', 'CANCELED')
                """
        );

        dropIndexIfExists("uk_success_user_slot");
        ensureIndex("idx_machine_id", "ALTER TABLE booking_record ADD INDEX idx_machine_id (machine_id)");
        ensureIndex("uk_user_slot", "ALTER TABLE booking_record ADD UNIQUE KEY uk_user_slot (user_id, slot_id)");
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
                  AND table_name = 'booking_record'
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
            log.warn("event=schema.booking_record.index.skip indexName={} reason={}", indexName, e.getMessage());
        }
    }

    private void dropIndexIfExists(String indexName) {
        if (indexExists(indexName)) {
            jdbcTemplate.execute("ALTER TABLE booking_record DROP INDEX " + indexName);
        }
    }

    private boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'booking_record'
                  AND index_name = ?
                """,
                Integer.class,
                indexName
        );
        return count != null && count > 0;
    }
}
