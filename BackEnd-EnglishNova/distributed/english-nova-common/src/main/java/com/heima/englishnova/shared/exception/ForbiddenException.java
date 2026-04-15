package com.heima.englishnova.shared.exception;

/**
 * 权限不足异常，当用户无权访问资源时抛出。
 */
public class ForbiddenException extends RuntimeException {

    /**
     * @param message 异常信息
     */
    public ForbiddenException(String message) {
        super(message);
    }
}
