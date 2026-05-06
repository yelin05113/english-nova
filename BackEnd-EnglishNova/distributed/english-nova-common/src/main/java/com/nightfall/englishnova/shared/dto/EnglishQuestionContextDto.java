package com.nightfall.englishnova.shared.dto;

public record EnglishQuestionContextDto(
        String word,
        String meaningCn,
        String exampleSentence,
        String correctedExampleSentence
) {
}
