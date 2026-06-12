package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ChangePasswordRequest", description = "User password change request")
public record ChangePasswordRequest(
        @Schema(description = "Current password")
        @NotBlank(message = "Current password cannot be blank")
        String currentPassword,
        @Schema(description = "New password")
        @NotBlank(message = "New password cannot be blank")
        @Size(min = 6, max = 64, message = "New password length must be between 6 and 64")
        String newPassword
) {
}
