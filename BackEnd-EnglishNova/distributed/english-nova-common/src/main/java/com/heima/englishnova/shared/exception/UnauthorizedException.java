package com.heima.englishnova.shared.exception;

/**
 * 未授权异常，当用户未登录或令牌无效时抛出。
 */
public class UnauthorizedException extends RuntimeException {

    /**
     * @param message 异常信息
     */
    public UnauthorizedException(String message) {
        super(message);
    }
}
