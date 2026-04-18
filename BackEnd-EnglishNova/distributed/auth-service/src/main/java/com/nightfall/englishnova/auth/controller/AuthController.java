package com.nightfall.englishnova.auth.controller;

import com.nightfall.englishnova.auth.service.AuthService;
import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.auth.RequestUserExtractor;
import com.nightfall.englishnova.shared.common.ApiResponse;
import com.nightfall.englishnova.shared.dto.AuthTokenResponse;
import com.nightfall.englishnova.shared.dto.AuthUserDto;
import com.nightfall.englishnova.shared.dto.LoginRequest;
import com.nightfall.englishnova.shared.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证相关 HTTP 接口控制器。
 * <p>提供用户注册、登录及当前登录用户信息查询等端点。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * 构造注入认证服务。
     *
     * @param authService 认证业务服务
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册接口。
     * <p>HTTP POST /api/auth/register</p>
     *
     * @param request 注册请求参数
     * @return 包含 JWT 令牌及用户信息的响应
     */
    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    /**
     * 用户登录接口。
     * <p>HTTP POST /api/auth/login</p>
     *
     * @param request 登录请求参数
     * @return 包含 JWT 令牌及用户信息的响应
     */
    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 获取当前登录用户信息接口。
     * <p>HTTP GET /api/auth/me</p>
     *
     * @param request HTTP 请求对象，用于提取当前登录用户
     * @return 当前登录用户基本信息
     */
    @GetMapping("/me")
    public ApiResponse<AuthUserDto> me(HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(new AuthUserDto(user.id(), user.username()));
    }
}
