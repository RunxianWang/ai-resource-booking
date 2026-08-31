package com.wrx.booking.auth;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(String username, String password) {}
    public record LoginResponse(Long userId, String username, java.util.Set<String> roles) {}
}
