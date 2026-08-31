package com.wrx.booking.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConsumerMetricRepository {

    public static final String DUPLICATE_CONSUMPTION = "consumer.duplicate_consumption";

    private final JdbcTemplate jdbcTemplate;

    public ConsumerMetricRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long increment(String metricKey) {
        jdbcTemplate.update(
                "INSERT INTO consumer_metric(metric_key, metric_value) VALUES (?, 1) ON DUPLICATE KEY UPDATE metric_value = metric_value + 1",
                metricKey
        );
        Long value = jdbcTemplate.queryForObject(
                "SELECT metric_value FROM consumer_metric WHERE metric_key = ?",
                Long.class,
                metricKey
        );
        return value == null ? 0L : value;
    }

    public long value(String metricKey) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT metric_value FROM consumer_metric WHERE metric_key = ?",
                Long.class,
                metricKey
        );
        return value == null ? 0L : value;
    }
}
