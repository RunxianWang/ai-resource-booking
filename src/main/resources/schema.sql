CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at DATETIME NULL,
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    INDEX idx_user_roles_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS resource_machine (
                                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                machine_name VARCHAR(128) NOT NULL,
                                                resource_type VARCHAR(32) NOT NULL,
                                                gpu_model VARCHAR(64) NOT NULL,
                                                status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                                                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                UNIQUE KEY uk_machine_name (machine_name)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS resource_slot (
                                             id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                             machine_id BIGINT NOT NULL,
                                             resource_id BIGINT NULL,
                                             resource_name VARCHAR(128) NOT NULL,
                                             resource_type VARCHAR(32) NOT NULL,
                                             start_time DATETIME NOT NULL,
                                             end_time DATETIME NOT NULL,
                                             total_count INT NOT NULL DEFAULT 1,
                                             available_count INT NOT NULL DEFAULT 1,
                                             status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
                                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                             updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                             UNIQUE KEY uk_machine_time (machine_id, start_time, end_time),
                                             INDEX idx_resource_time (machine_id, start_time, end_time),
                                             INDEX idx_slot_status_end_time (status, end_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS booking_record (
                                              id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                              user_id BIGINT NOT NULL,
                                              slot_id BIGINT NOT NULL,
                                              machine_id BIGINT NOT NULL,
                                              status VARCHAR(32) NOT NULL DEFAULT 'RESERVED',
                                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                              UNIQUE KEY uk_user_slot (user_id, slot_id),
                                              INDEX idx_slot_id (slot_id),
                                              INDEX idx_machine_id (machine_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS message_log (
                                           id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                           booking_id BIGINT NOT NULL,
                                           message_key VARCHAR(128) NOT NULL,
                                           topic VARCHAR(128) NOT NULL,
                                           event_type VARCHAR(64) NOT NULL,
                                           payload TEXT NOT NULL,
                                           status VARCHAR(32) NOT NULL DEFAULT 'INIT',
                                           retry_count INT NOT NULL DEFAULT 0,
                                           last_error TEXT NULL,
                                           created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_key (message_key),
    INDEX idx_status_created_at (status, created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consume_log (
                                           id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                           message_key VARCHAR(128) NOT NULL,
                                           consumer_group VARCHAR(128) NOT NULL,
                                           status VARCHAR(32) NOT NULL,
                                           created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_consumer_group (message_key, consumer_group)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS booking_event_projection (
                                           id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                           booking_id BIGINT NOT NULL,
                                           user_id BIGINT NULL,
                                           slot_id BIGINT NULL,
                                           event_type VARCHAR(64) NOT NULL,
                                           booking_status VARCHAR(32) NOT NULL,
                                           last_message_key VARCHAR(128) NOT NULL,
                                           event_time VARCHAR(64) NULL,
                                           created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_projection_booking (booking_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS booking_event_audit (
                                           id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                           booking_id BIGINT NOT NULL,
                                           message_key VARCHAR(128) NOT NULL,
                                           consumer_group VARCHAR(128) NOT NULL,
                                           event_type VARCHAR(64) NOT NULL,
                                           processing_status VARCHAR(32) NOT NULL,
                                           error_message TEXT NULL,
                                           created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_audit_message_consumer (message_key, consumer_group)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dead_letter_log (
                                           id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                           message_key VARCHAR(128) NOT NULL,
                                           consumer_group VARCHAR(128) NOT NULL,
                                           original_topic VARCHAR(128) NOT NULL,
                                           original_partition INT NULL,
                                           original_offset BIGINT NULL,
                                           payload TEXT NOT NULL,
                                           exception_class VARCHAR(255) NULL,
                                           exception_message TEXT NULL,
                                           status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                                           retry_count INT NOT NULL DEFAULT 0,
                                           replay_count INT NOT NULL DEFAULT 0,
                                           created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           replayed_at DATETIME NULL,
                                           updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dead_letter_origin (message_key, consumer_group, original_topic, original_partition, original_offset),
    INDEX idx_dead_letter_status_created (status, created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consumer_metric (
                                           metric_key VARCHAR(128) PRIMARY KEY,
                                           metric_value BIGINT NOT NULL DEFAULT 0,
                                           updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

