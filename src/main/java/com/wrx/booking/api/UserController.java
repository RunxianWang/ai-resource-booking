package com.wrx.booking.api;

import com.wrx.booking.api.dto.CurrentUserResponse;
import com.wrx.booking.support.DemoUserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final DemoUserContext demoUserContext;

    public UserController(DemoUserContext demoUserContext) {
        this.demoUserContext = demoUserContext;
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        return new CurrentUserResponse(demoUserContext.userId(), demoUserContext.userName());
    }
}
