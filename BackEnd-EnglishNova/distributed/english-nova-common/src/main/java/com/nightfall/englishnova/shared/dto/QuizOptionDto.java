package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "QuizOptionDto", description = "Quiz answer option display detail")
public record QuizOptionDto(
        @Schema(description = "Submitted option value")
        String value,
        @Schema(description = "English word")
        String word,
        @Schema(description = "Chinese meaning")
        String meaningCn
) {
}
