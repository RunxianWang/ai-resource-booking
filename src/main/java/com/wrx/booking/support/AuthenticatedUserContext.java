package com.wrx.booking.support;

import com.wrx.booking.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!perf")
public class AuthenticatedUserContext implements CurrentUserProvider {
    public static final String REQUEST_USER_ATTRIBUTE = "authenticatedUser";

    private final HttpServletRequest request;

    public AuthenticatedUserContext(HttpServletRequest request) {
        this.request = request;
    }

    private AuthenticatedUser user() {
        AuthenticatedUser user = (AuthenticatedUser) request.getAttribute(REQUEST_USER_ATTRIBUTE);
        if (user == null) {
            throw new IllegalStateException("authenticated user is required");
        }
        return user;
    }

    @Override
    public Long userId() {
        return user().id();
    }

    @Override
    public String userName() {
        return user().username();
    }
}
