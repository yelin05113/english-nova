package com.nightfall.englishnova.shared.exception;

import com.nightfall.englishnova.shared.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，捕获各类异常并转换为统一的 API 响应格式。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理请求参数校验异常。
     *
     * @param exception 校验异常
     * @return 包含校验错误信息的失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("request validation failed");
        return ApiResponse.failure(message);
    }

    /**
     * 处理约束违反异常。
     *
     * @param exception 约束违反异常
     * @return 包含约束错误信息的失败响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException exception) {
        return ApiResponse.failure(exception.getMessage());
    }

    /**
     * 处理非法参数异常。
     *
     * @param exception 非法参数异常
     * @return 包含错误信息的失败响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException exception) {
        return ApiResponse.failure(exception.getMessage());
    }

    /**
     * 处理资源未找到异常。
     *
     * @param exception 资源未找到异常
     * @return 包含错误信息的失败响应
     */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(NotFoundException exception) {
        return ApiResponse.failure(exception.getMessage());
    }

    /**
     * 处理未授权异常。
     *
     * @param exception 未授权异常
     * @return 包含错误信息的失败响应
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleUnauthorized(UnauthorizedException exception) {
        return ApiResponse.failure(exception.getMessage());
    }

    /**
     * 处理权限不足异常。
     *
     * @param exception 权限不足异常
     * @return 包含错误信息的失败响应
     */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbidden(ForbiddenException exception) {
        return ApiResponse.failure(exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleConflict(ConflictException exception) {
        return ApiResponse.failure(exception.getMessage());
    }
}
