package com.nightfall.englishnova.shared.auth;

import com.nightfall.englishnova.shared.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * HTTP 请求用户信息提取器，从网关透传的请求头中解析当前登录用户。
 */
public final class RequestUserExtractor {

    private RequestUserExtractor() {
    }

    /**
     * 从请求中强制提取当前用户，若未登录则抛出 UnauthorizedException。
     *
     * @param request HTTP 请求
     * @return 当前登录用户
     * @throws UnauthorizedException 未登录或令牌无效时抛出
     */
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

    /**
     * 从请求中可选提取当前用户，若未登录则返回 null。
     *
     * @param request HTTP 请求
     * @return 当前登录用户，未登录时为 null
     */
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
