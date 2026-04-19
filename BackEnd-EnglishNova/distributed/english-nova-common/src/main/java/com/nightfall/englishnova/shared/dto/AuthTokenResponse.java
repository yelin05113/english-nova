package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthTokenResponse", description = "Authentication token response")
public record AuthTokenResponse(
        @Schema(description = "JWT access token")
        String accessToken,
        @Schema(description = "Authenticated user")
        AuthUserDto user
) {
}
