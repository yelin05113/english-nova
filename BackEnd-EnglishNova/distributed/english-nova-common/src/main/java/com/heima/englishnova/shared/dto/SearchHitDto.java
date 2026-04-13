package com.heima.englishnova.shared.dto;

public record SearchHitDto(
        Long entryId,
        String word,
        String phonetic,
        String meaningCn,
        String source,
        String exampleSentence,
        String category,
        String visibility
) {
}
