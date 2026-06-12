package com.nightfall.englishnova.auth.service;

import com.nightfall.englishnova.shared.dto.AuthTokenResponse;
import com.nightfall.englishnova.shared.dto.AuthUserDto;
import com.nightfall.englishnova.shared.dto.ChangePasswordRequest;
import com.nightfall.englishnova.shared.dto.LoginRequest;
import com.nightfall.englishnova.shared.dto.RegisterRequest;
import com.nightfall.englishnova.shared.dto.UpdateQuizOptionStrategyRequest;
import com.nightfall.englishnova.shared.dto.UpdateProfileRequest;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证业务服务接口。
 */
public interface AuthService {

    AuthTokenResponse register(RegisterRequest request);

    AuthTokenResponse login(LoginRequest request);

    void changePassword(long userId, ChangePasswordRequest request);

    AuthUserDto getCurrentUser(long userId, String username);

    AuthTokenResponse updateProfile(long userId, UpdateProfileRequest request);

    AuthUserDto updateQuizOptionStrategy(long userId, UpdateQuizOptionStrategyRequest request);

    AuthTokenResponse updateAvatar(long userId, MultipartFile file);
}
