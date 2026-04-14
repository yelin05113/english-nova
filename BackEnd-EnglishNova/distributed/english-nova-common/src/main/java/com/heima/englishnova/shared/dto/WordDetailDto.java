package com.heima.englishnova.shared.dto;

public record WordDetailDto(
        long entryId,
        Long ownerUserId,
        long wordbookId,
        String wordbookName,
        String word,
        String phonetic,
        String meaningCn,
        String exampleSentence,
        String category,
        int difficulty,
        String visibility,
        String source,
        String sourceName,
        String importSource,
        String audioUrl
) {
}
