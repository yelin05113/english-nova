package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "StudyAgendaDto", description = "Study agenda")
public record StudyAgendaDto(
        @Schema(description = "New card count")
        int newCards,
        @Schema(description = "Review card count")
        int reviewCards,
        @Schema(description = "Listening card count")
        int listeningCards,
        @Schema(description = "Estimated study minutes")
        int estimatedMinutes,
        @Schema(description = "Focus areas")
        List<String> focusAreas
) {
}
