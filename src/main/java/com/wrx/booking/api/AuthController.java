package com.wrx.booking.api;

import com.wrx.booking.auth.AuthDtos;
import com.wrx.booking.auth.AuthService;
import com.wrx.booking.auth.AuthenticatedUser;
import com.wrx.booking.auth.JwtService;
import com.wrx.booking.support.AuthenticatedUserContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String COOKIE_NAME = "AUTH_TOKEN";
    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService; this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthenticatedUser user = authService.login(request.username(), request.password());
        Cookie cookie = new Cookie(COOKIE_NAME, jwtService.issue(user));
        cookie.setHttpOnly(true); cookie.setPath("/"); cookie.setMaxAge(7200);
        response.addCookie(cookie);
        return new AuthDtos.LoginResponse(user.id(), user.username(), user.roles());
    }

    @PostMapping("/register")
    public AuthDtos.LoginResponse register(@Valid @RequestBody LoginRequest request) {
        AuthenticatedUser user = authService.register(request.username(), request.password());
        return new AuthDtos.LoginResponse(user.id(), user.username(), user.roles());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true); cookie.setPath("/"); cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthDtos.LoginResponse me(@RequestAttribute(AuthenticatedUserContext.REQUEST_USER_ATTRIBUTE) AuthenticatedUser user) {
        return new AuthDtos.LoginResponse(user.id(), user.username(), user.roles());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
