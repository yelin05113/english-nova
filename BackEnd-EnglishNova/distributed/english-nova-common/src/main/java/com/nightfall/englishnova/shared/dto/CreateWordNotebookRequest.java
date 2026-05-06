package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateWordNotebookRequest", description = "Create word notebook request")
public record CreateWordNotebookRequest(
        @Schema(description = "Word notebook name")
        @NotBlank(message = "Word notebook name cannot be empty")
        @Size(max = 120, message = "Word notebook name is too long")
        String name
) {
}
