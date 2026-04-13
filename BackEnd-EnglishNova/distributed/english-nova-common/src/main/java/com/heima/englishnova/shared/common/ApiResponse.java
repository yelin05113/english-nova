package com.heima.englishnova.shared.common;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        OffsetDateTime timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, "ok", OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> failure(String message) {
        return new ApiResponse<>(false, null, message, OffsetDateTime.now());
    }
}
