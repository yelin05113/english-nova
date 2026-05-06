package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AddWordNotebookEntryResultDto", description = "Add word notebook entry result")
public record AddWordNotebookEntryResultDto(
        @Schema(description = "Whether a new entry was created")
        boolean added,
        @Schema(description = "Updated notebook summary")
        WordNotebookSummaryDto notebook,
        @Schema(description = "Notebook entry detail")
        WordNotebookEntryDto entry
) {
}
