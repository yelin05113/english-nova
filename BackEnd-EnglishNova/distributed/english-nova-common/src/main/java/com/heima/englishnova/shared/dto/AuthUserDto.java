package com.heima.englishnova.shared.dto;

/**
 * 认证用户信息。
 *
 * @param id       用户 ID
 * @param username 用户名
 */
public record AuthUserDto(
        long id,
        String username
) {
}
