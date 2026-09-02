package com.wrx.booking.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder;
    private final String adminUsername;
    private final String adminPassword;

    public AuthInitializer(JdbcTemplate jdbc, BCryptPasswordEncoder encoder,
                           @Value("${app.bootstrap-admin-username}") String adminUsername,
                           @Value("${app.bootstrap-admin-password}") String adminPassword) {
        this.jdbc = jdbc; this.encoder = encoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        jdbc.update("INSERT IGNORE INTO roles(code, name) VALUES ('USER', '普通用户'), ('ADMIN', '管理员')");
        String hash = encoder.encode(adminPassword);
        jdbc.update("INSERT IGNORE INTO users(id, username, password_hash, status) VALUES (1, ?, ?, 'ACTIVE')", adminUsername, hash);
        Long adminRoleId = jdbc.queryForObject("SELECT id FROM roles WHERE code = 'ADMIN'", Long.class);
        jdbc.update("INSERT IGNORE INTO user_roles(user_id, role_id) VALUES (1, ?)", adminRoleId);
    }
}
