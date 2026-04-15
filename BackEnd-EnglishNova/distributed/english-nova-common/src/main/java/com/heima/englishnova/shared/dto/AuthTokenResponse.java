package com.heima.englishnova.shared.dto;

/**
 * 认证令牌响应，包含 JWT 访问令牌和用户信息。
 *
 * @param accessToken JWT 访问令牌
 * @param user        用户信息
 */
public record AuthTokenResponse(
        String accessToken,
        AuthUserDto user
) {
}
