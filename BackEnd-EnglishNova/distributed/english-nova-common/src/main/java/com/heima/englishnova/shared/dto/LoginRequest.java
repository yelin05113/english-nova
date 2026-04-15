package com.heima.englishnova.shared.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户登录请求。
 *
 * @param account  账号（用户名或邮箱）
 * @param password 密码
 */
public record LoginRequest(
        @NotBlank(message = "账号不能为空")
        String account,
        @NotBlank(message = "密码不能为空")
        String password
) {
}
