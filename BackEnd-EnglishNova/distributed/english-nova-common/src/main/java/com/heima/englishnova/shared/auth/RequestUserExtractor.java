package com.heima.englishnova.shared.auth;

import com.heima.englishnova.shared.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

public final class RequestUserExtractor {

    private RequestUserExtractor() {
    }

    public static CurrentUser require(HttpServletRequest request) {
        String rawUserId = request.getHeader(AuthHeaders.USER_ID);
        String username = request.getHeader(AuthHeaders.USERNAME);
        if (rawUserId == null || rawUserId.isBlank() || username == null || username.isBlank()) {
            throw new UnauthorizedException("请先登录");
        }
        try {
            return new CurrentUser(Long.parseLong(rawUserId), username);
        } catch (NumberFormatException exception) {
            throw new UnauthorizedException("无效的登录上下文");
        }
    }

    public static CurrentUser optional(HttpServletRequest request) {
        String rawUserId = request.getHeader(AuthHeaders.USER_ID);
        String username = request.getHeader(AuthHeaders.USERNAME);
        if (rawUserId == null || rawUserId.isBlank() || username == null || username.isBlank()) {
            return null;
        }
        try {
            return new CurrentUser(Long.parseLong(rawUserId), username);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
