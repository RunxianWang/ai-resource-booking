package com.wrx.booking.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DuplicateKeyException;

import java.util.HashSet;

@Repository
public class AuthRepository {
    private final JdbcTemplate jdbc;

    public AuthRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UserRecord findRecord(String username) {
        return jdbc.query("SELECT u.id, u.username, u.password_hash, u.status, r.code FROM users u LEFT JOIN user_roles ur ON ur.user_id = u.id LEFT JOIN roles r ON r.id = ur.role_id WHERE u.username = ?", rs -> {
            if (!rs.next()) return null;
            var roles = new HashSet<String>();
            Long id = rs.getLong("id"); String name = rs.getString("username"); String hash = rs.getString("password_hash"); String status = rs.getString("status");
            do { if (rs.getString("code") != null) roles.add(rs.getString("code")); } while (rs.next());
            return new UserRecord(new AuthenticatedUser(id, name, roles), hash, status);
        }, username);
    }

    public void updateLastLogin(Long userId) { jdbc.update("UPDATE users SET last_login_at = NOW() WHERE id = ?", userId); }

    public Long createUser(String username, String passwordHash) {
        jdbc.update("INSERT INTO users(username, password_hash, status) VALUES (?, ?, 'ACTIVE')", username, passwordHash);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        Long roleId = jdbc.queryForObject("SELECT id FROM roles WHERE code = 'USER'", Long.class);
        jdbc.update("INSERT INTO user_roles(user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    public record UserRecord(AuthenticatedUser user, String passwordHash, String status) {}
}
