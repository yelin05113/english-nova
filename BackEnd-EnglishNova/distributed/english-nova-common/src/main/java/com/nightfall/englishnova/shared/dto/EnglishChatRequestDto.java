package com.nightfall.englishnova.shared.dto;

import java.util.List;

public record EnglishChatRequestDto(
        List<EnglishChatMessageDto> messages,
        EnglishQuestionContextDto questionContext,
        String userPrompt
) {
}
