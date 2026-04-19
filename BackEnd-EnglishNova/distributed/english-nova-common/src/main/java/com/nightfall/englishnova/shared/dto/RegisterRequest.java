package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterRequest", description = "User registration request")
public record RegisterRequest(
        @Schema(description = "Username")
        @NotBlank(message = "Username cannot be blank")
        @Size(min = 3, max = 32, message = "Username length must be between 3 and 32")
        String username,
        @Schema(description = "Email address")
        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Email format is invalid")
        String email,
        @Schema(description = "Password")
        @NotBlank(message = "Password cannot be blank")
        @Size(min = 6, max = 64, message = "Password length must be between 6 and 64")
        String password
) {
}
