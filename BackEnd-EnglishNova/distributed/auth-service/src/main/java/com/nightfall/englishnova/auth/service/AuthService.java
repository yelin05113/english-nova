package com.nightfall.englishnova.auth.service;

import com.nightfall.englishnova.shared.dto.AuthTokenResponse;
import com.nightfall.englishnova.shared.dto.LoginRequest;
import com.nightfall.englishnova.shared.dto.RegisterRequest;

/**
 * 认证业务服务接口。
 */
public interface AuthService {

    AuthTokenResponse register(RegisterRequest request);

    AuthTokenResponse login(LoginRequest request);
}
