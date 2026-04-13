package com.heima.englishnova.shared.dto;

public record AuthTokenResponse(
        String accessToken,
        AuthUserDto user
) {
}
