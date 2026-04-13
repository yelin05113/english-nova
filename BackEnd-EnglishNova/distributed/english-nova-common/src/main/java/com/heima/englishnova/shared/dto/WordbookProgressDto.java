package com.heima.englishnova.shared.dto;

public record WordbookProgressDto(
        long wordbookId,
        int wordCount,
        int clearedCount,
        int inProgressCount,
        int pendingCount
) {
}
