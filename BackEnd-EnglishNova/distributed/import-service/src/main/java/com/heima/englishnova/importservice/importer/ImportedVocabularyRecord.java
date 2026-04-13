package com.heima.englishnova.importservice.importer;

public record ImportedVocabularyRecord(
        String word,
        String phonetic,
        String meaning,
        String exampleSentence,
        String category,
        int difficulty
) {
}
