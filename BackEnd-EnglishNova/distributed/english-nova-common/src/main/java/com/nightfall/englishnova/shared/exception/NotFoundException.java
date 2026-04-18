package com.nightfall.englishnova.shared.exception;

/**
 * 资源未找到异常，当请求的实体不存在时抛出。
 */
public class NotFoundException extends RuntimeException {

    /**
     * @param message 异常信息
     */
    public NotFoundException(String message) {
        super(message);
    }
}
