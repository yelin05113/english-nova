package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "User login request")
public record LoginRequest(
        @Schema(description = "Username or email")
        @NotBlank(message = "Account cannot be blank")
        String account,
        @Schema(description = "Password")
        @NotBlank(message = "Password cannot be blank")
        String password
) {
}
