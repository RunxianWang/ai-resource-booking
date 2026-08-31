package com.wrx.booking.api;

import com.wrx.booking.api.dto.CurrentUserResponse;
import com.wrx.booking.support.CurrentUserProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final CurrentUserProvider currentUserProvider;

    public UserController(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        return new CurrentUserResponse(currentUserProvider.userId(), currentUserProvider.userName());
    }
}
