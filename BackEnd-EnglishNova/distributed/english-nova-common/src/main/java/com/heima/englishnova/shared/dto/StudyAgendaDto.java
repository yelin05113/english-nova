package com.heima.englishnova.shared.dto;

import java.util.List;

public record StudyAgendaDto(
        int newCards,
        int reviewCards,
        int listeningCards,
        int estimatedMinutes,
        List<String> focusAreas
) {
}
