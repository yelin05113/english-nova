package com.nightfall.englishnova.shared.dto;

import com.nightfall.englishnova.shared.enums.VocabularyEntryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "AddWordNotebookEntryRequest", description = "Add word notebook entry request")
public record AddWordNotebookEntryRequest(
        @Schema(description = "Word")
        @NotBlank(message = "Word cannot be empty")
        @Size(max = 120, message = "Word is too long")
        String word,
        @Schema(description = "Phonetic")
        @Size(max = 120, message = "Phonetic is too long")
        String phonetic,
        @Schema(description = "Chinese meaning")
        @Size(max = 255, message = "Meaning is too long")
        String meaningCn,
        @Schema(description = "Original example sentence")
        @Size(max = 255, message = "Example sentence is too long")
        String exampleSentence,
        @Schema(description = "Corrected example sentence")
        @Size(max = 255, message = "Corrected example sentence is too long")
        String correctedExampleSentence,
        @Schema(description = "Chinese sentence")
        @Size(max = 255, message = "Chinese sentence is too long")
        String chineseSentence,
        @Schema(description = "Example audio url")
        @Size(max = 255, message = "Example audio url is too long")
        String exampleAudioUrl,
        @Schema(description = "Saved quiz options in display order")
        List<QuizOptionDto> optionDetails,
        @Schema(description = "Saved correct option value")
        @Size(max = 255, message = "Correct option is too long")
        String correctOption,
        @Schema(description = "Source entry type")
        VocabularyEntryType sourceEntryType,
        @Schema(description = "Source entry id")
        Long sourceEntryId
) {
}
