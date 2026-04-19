package com.nightfall.englishnova.auth.service.impl;

import com.nightfall.englishnova.auth.domain.po.UserPo;
import com.nightfall.englishnova.auth.mapper.UserMapper;
import com.nightfall.englishnova.auth.service.AuthService;
import com.nightfall.englishnova.auth.service.JwtTokenService;
import com.nightfall.englishnova.shared.dto.AuthTokenResponse;
import com.nightfall.englishnova.shared.dto.AuthUserDto;
import com.nightfall.englishnova.shared.dto.LoginRequest;
import com.nightfall.englishnova.shared.dto.RegisterRequest;
import com.nightfall.englishnova.shared.enums.UserStatus;
import com.nightfall.englishnova.shared.exception.ForbiddenException;
import com.nightfall.englishnova.shared.exception.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthServiceImpl(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        ensureUnique(username, email);

        UserPo user = new UserPo();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE.name());
        userMapper.insert(user);

        if (user.getId() == null) {
            throw new IllegalArgumentException("注册失败，请稍后重试");
        }

        String token = jwtTokenService.issueToken(user.getId(), username);
        return new AuthTokenResponse(token, new AuthUserDto(user.getId(), username));
    }

    @Override
    public AuthTokenResponse login(LoginRequest request) {
        String account = request.account().trim();
        UserPo user = userMapper.findByAccount(account, account.toLowerCase(Locale.ROOT));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("账号或密码错误");
        }
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new ForbiddenException("账号已被禁用");
        }
        String token = jwtTokenService.issueToken(user.getId(), user.getUsername());
        return new AuthTokenResponse(token, new AuthUserDto(user.getId(), user.getUsername()));
    }

    @Override
    public AuthUserDto getCurrentUser(long userId, String username) {
        UserPo user = userMapper.selectById(userId);
        if (user == null || !user.getUsername().equals(username)) {
            throw new UnauthorizedException("登录已失效，请重新登录");
        }
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new ForbiddenException("账号已被禁用");
        }
        return new AuthUserDto(user.getId(), user.getUsername());
    }

    private void ensureUnique(String username, String email) {
        if (userMapper.countByUsername(username) > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (userMapper.countByEmail(email) > 0) {
            throw new IllegalArgumentException("邮箱已存在");
        }
    }
}
