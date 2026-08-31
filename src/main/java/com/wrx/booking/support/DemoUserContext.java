package com.wrx.booking.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!perf")
public class DemoUserContext implements CurrentUserProvider {

    private final Long userId;
    private final String userName;

    public DemoUserContext(
            @Value("${app.demo-user-id:1}") Long userId,
            @Value("${app.demo-user-name:演示用户 1}") String userName
    ) {
        this.userId = userId;
        this.userName = userName;
    }

    public Long userId() {
        return userId;
    }

    public String userName() {
        return userName;
    }
}
