package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthUserDto", description = "Authenticated user summary")
public record AuthUserDto(
        @Schema(description = "User id")
        long id,
        @Schema(description = "Username")
        String username
) {
}
