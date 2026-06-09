package com.wrx.booking.api.dto;

public record CurrentUserResponse(
        Long userId,
        String userName
) {
}
