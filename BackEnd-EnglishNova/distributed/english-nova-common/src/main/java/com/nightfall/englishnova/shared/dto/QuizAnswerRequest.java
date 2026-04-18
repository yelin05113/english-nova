package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(name = "QuizAnswerRequest", description = "Quiz answer submission request")
public record QuizAnswerRequest(
        @Schema(description = "Attempt id")
        @NotNull(message = "Attempt id cannot be null")
        Long attemptId,
        @Schema(description = "Selected option")
        @NotBlank(message = "Selected option cannot be blank")
        String selectedOption
) {
}
