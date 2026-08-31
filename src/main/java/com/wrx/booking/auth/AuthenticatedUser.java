package com.wrx.booking.auth;

import java.util.Set;

public record AuthenticatedUser(Long id, String username, Set<String> roles) {
    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }
}
