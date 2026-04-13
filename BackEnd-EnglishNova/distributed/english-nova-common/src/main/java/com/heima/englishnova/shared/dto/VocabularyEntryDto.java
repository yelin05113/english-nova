package com.heima.englishnova.shared.dto;

public record VocabularyEntryDto(
        long id,
        String word,
        String phonetic,
        String meaningCn,
        String exampleSentence,
        String category,
        int difficulty,
        String visibility
) {
}
