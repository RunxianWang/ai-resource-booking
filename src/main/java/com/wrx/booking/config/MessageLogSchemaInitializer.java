package com.wrx.booking.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class MessageLogSchemaInitializer implements InitializingBean {

    private static final DateTimeFormatter BACKUP_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbcTemplate;

    public MessageLogSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        if (!tableExists("message_log")) {
            createMessageLogTable();
            return;
        }

        if (!hasAutoIncrementBigintId()) {
            rebuildLegacyMessageLogTable();
            return;
        }

        ensureCanonicalColumns();
        dropColumnIfExists("biz_key");
        dropColumnIfExists("error_msg");
        ensureIndex("uk_message_key", "ALTER TABLE message_log ADD UNIQUE KEY uk_message_key (message_key)");
        ensureIndex("idx_status_created_at", "ALTER TABLE message_log ADD INDEX idx_status_created_at (status, created_at)");
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

    private boolean hasAutoIncrementBigintId() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'message_log'
                  AND column_name = 'id'
                  AND data_type = 'bigint'
                  AND extra LIKE '%auto_increment%'
                """,
                Integer.class
        );
        return count != null && count > 0;
    }

    private void rebuildLegacyMessageLogTable() {
        String backupTableName = "message_log_legacy_" + LocalDateTime.now().format(BACKUP_SUFFIX_FORMATTER);
        jdbcTemplate.execute("RENAME TABLE message_log TO " + backupTableName);
        createMessageLogTable();
    }

    private void createMessageLogTable() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS message_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    booking_id BIGINT NOT NULL,
                    message_key VARCHAR(128) NOT NULL,
                    topic VARCHAR(128) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    payload TEXT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    retry_count INT NOT NULL DEFAULT 0,
                    last_error TEXT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_message_key (message_key),
                    INDEX idx_status_created_at (status, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
        );
    }

    private void ensureCanonicalColumns() {
        ensureColumn("booking_id", "ALTER TABLE message_log ADD COLUMN booking_id BIGINT NOT NULL AFTER id");
        ensureColumn("message_key", "ALTER TABLE message_log ADD COLUMN message_key VARCHAR(128) NOT NULL AFTER booking_id");
        ensureColumn("topic", "ALTER TABLE message_log ADD COLUMN topic VARCHAR(128) NOT NULL AFTER message_key");
        ensureColumn("event_type", "ALTER TABLE message_log ADD COLUMN event_type VARCHAR(64) NOT NULL AFTER topic");
        ensureColumn("payload", "ALTER TABLE message_log ADD COLUMN payload TEXT NOT NULL AFTER event_type");
        ensureColumn("status", "ALTER TABLE message_log ADD COLUMN status VARCHAR(32) NOT NULL AFTER payload");
        ensureColumn("retry_count", "ALTER TABLE message_log ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER status");
        ensureColumn("last_error", "ALTER TABLE message_log ADD COLUMN last_error TEXT NULL AFTER retry_count");
        ensureColumn("created_at", "ALTER TABLE message_log ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER last_error");
        ensureColumn("updated_at", "ALTER TABLE message_log ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at");
    }

    private void ensureColumn(String columnName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'message_log'
                  AND column_name = ?
                """,
                Integer.class,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(alterSql);
        }
    }

    private void dropColumnIfExists(String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'message_log'
                  AND column_name = ?
                """,
                Integer.class,
                columnName
        );
        if (count != null && count > 0) {
            jdbcTemplate.execute("ALTER TABLE message_log DROP COLUMN " + columnName);
        }
    }

    private void ensureIndex(String indexName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'message_log'
                  AND index_name = ?
                """,
                Integer.class,
                indexName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(alterSql);
        }
    }
}
