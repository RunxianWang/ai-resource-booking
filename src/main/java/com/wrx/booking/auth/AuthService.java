package com.wrx.booking.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AuthRepository repository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(AuthRepository repository, JwtService jwtService, BCryptPasswordEncoder passwordEncoder) {
        this.repository = repository; this.jwtService = jwtService; this.passwordEncoder = passwordEncoder;
    }

    public AuthenticatedUser login(String username, String password) {
        AuthRepository.UserRecord record = repository.findRecord(username);
        if (record == null || !"ACTIVE".equals(record.status()) || !passwordEncoder.matches(password, record.passwordHash())) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        repository.updateLastLogin(record.user().id());
        return record.user();
    }

    public String token(AuthenticatedUser user) { return jwtService.issue(user); }

    public AuthenticatedUser register(String username, String password) {
        try {
            Long id = repository.createUser(username, passwordEncoder.encode(password));
            return new AuthenticatedUser(id, username, java.util.Set.of("USER"));
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }
}
