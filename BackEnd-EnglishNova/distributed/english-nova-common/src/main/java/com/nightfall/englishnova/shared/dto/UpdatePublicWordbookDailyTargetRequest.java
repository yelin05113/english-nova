package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdatePublicWordbookDailyTargetRequest", description = "Public wordbook daily target update request")
public record UpdatePublicWordbookDailyTargetRequest(
        @Schema(description = "Daily target count", minimum = "0", maximum = "1000")
        @NotNull(message = "Daily target count cannot be null")
        @Min(value = 0, message = "Daily target count cannot be negative")
        @Max(value = 1000, message = "Daily target count cannot exceed 1000")
        Integer dailyTargetCount
) {
}
