package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateProfileRequest", description = "User profile update request")
public record UpdateProfileRequest(
        @Schema(description = "Username")
        @NotBlank(message = "Username cannot be blank")
        @Size(min = 1, max = 32, message = "Username length must be between 1 and 32")
        String username,
        @Schema(description = "Avatar image URL")
        @Size(max = 512, message = "Avatar URL cannot exceed 512 characters")
        String avatarUrl
) {
}
