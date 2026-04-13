package com.heima.englishnova.auth.controller;

import com.heima.englishnova.auth.service.AuthService;
import com.heima.englishnova.shared.auth.CurrentUser;
import com.heima.englishnova.shared.auth.RequestUserExtractor;
import com.heima.englishnova.shared.common.ApiResponse;
import com.heima.englishnova.shared.dto.AuthTokenResponse;
import com.heima.englishnova.shared.dto.AuthUserDto;
import com.heima.englishnova.shared.dto.LoginRequest;
import com.heima.englishnova.shared.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserDto> me(HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(new AuthUserDto(user.id(), user.username()));
    }
}
