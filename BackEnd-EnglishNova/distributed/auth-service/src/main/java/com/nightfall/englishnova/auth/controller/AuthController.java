package com.nightfall.englishnova.auth.controller;

import com.nightfall.englishnova.auth.service.AuthService;
import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.auth.RequestUserExtractor;
import com.nightfall.englishnova.shared.common.ApiResponse;
import com.nightfall.englishnova.shared.dto.AuthTokenResponse;
import com.nightfall.englishnova.shared.dto.AuthUserDto;
import com.nightfall.englishnova.shared.dto.LoginRequest;
import com.nightfall.englishnova.shared.dto.RegisterRequest;
import com.nightfall.englishnova.shared.dto.UpdateProfileRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/auth", "/auth"})
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
        return ApiResponse.success(authService.getCurrentUser(user.id(), user.username()));
    }

    @PatchMapping("/profile")
    public ApiResponse<AuthTokenResponse> updateProfile(
            HttpServletRequest servletRequest,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        CurrentUser user = RequestUserExtractor.require(servletRequest);
        return ApiResponse.success(authService.updateProfile(user.id(), request));
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AuthTokenResponse> updateAvatar(
            HttpServletRequest servletRequest,
            @RequestPart("file") MultipartFile file
    ) {
        CurrentUser user = RequestUserExtractor.require(servletRequest);
        return ApiResponse.success(authService.updateAvatar(user.id(), file));
    }
}
