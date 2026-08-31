package com.wrx.booking.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("perf")
public class HeaderCurrentUserProvider implements CurrentUserProvider {
    private final HttpServletRequest request;

    public HeaderCurrentUserProvider(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public Long userId() {
        String value = request.getHeader("X-Test-User-Id");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("X-Test-User-Id is required in perf profile");
        }
        return Long.valueOf(value);
    }

    @Override
    public String userName() {
        return "perf-user-" + userId();
    }
}
