package com.nightfall.englishnova.shared.auth;

/**
 * 当前登录用户信息记录，由网关从 JWT 令牌中解析并透传到下游服务。
 *
 * @param id       用户唯一标识
 * @param username 用户名
 */
public record CurrentUser(
        long id,
        String username
) {
}
