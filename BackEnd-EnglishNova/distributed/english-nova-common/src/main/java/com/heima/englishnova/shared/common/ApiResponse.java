package com.heima.englishnova.shared.common;

import java.time.OffsetDateTime;

/**
 * 统一 API 响应封装，包含成功标志、数据、消息和时间戳。
 *
 * @param <T>       响应数据类型
 * @param success   是否成功
 * @param data      响应数据
 * @param message   响应消息
 * @param timestamp 时间戳
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        OffsetDateTime timestamp
) {

    /**
     * 构建成功响应。
     *
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, "ok", OffsetDateTime.now());
    }

    /**
     * 构建失败响应。
     *
     * @param message 错误消息
     * @param <T>     响应数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failure(String message) {
        return new ApiResponse<>(false, null, message, OffsetDateTime.now());
    }
}
