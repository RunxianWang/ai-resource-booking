package com.wrx.booking.auth;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private final JwtService service = new JwtService("test-secret", 3600);

    @Test
    void issueAndParseRoundTrip() {
        AuthenticatedUser original = new AuthenticatedUser(1L, "admin", Set.of("ADMIN", "USER"));

        AuthenticatedUser parsed = service.parse(service.issue(original));

        assertEquals(original.id(), parsed.id());
        assertEquals(original.username(), parsed.username());
        assertEquals(original.roles(), parsed.roles());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = service.issue(new AuthenticatedUser(1L, "admin", Set.of("ADMIN")));

        assertThrows(IllegalArgumentException.class, () -> service.parse(token + "x"));
    }
}
