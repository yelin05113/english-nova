package com.nightfall.englishnova.auth.service.impl;

import com.nightfall.englishnova.auth.config.AuthServiceConfig;
import com.nightfall.englishnova.auth.domain.po.UserPo;
import com.nightfall.englishnova.auth.mapper.UserMapper;
import com.nightfall.englishnova.auth.service.AuthService;
import com.nightfall.englishnova.auth.service.JwtTokenService;
import com.nightfall.englishnova.auth.service.UserAvatarStorageService;
import com.nightfall.englishnova.shared.dto.AuthTokenResponse;
import com.nightfall.englishnova.shared.dto.AuthUserDto;
import com.nightfall.englishnova.shared.dto.ChangePasswordRequest;
import com.nightfall.englishnova.shared.dto.LoginRequest;
import com.nightfall.englishnova.shared.dto.RegisterRequest;
import com.nightfall.englishnova.shared.dto.UpdateProfileRequest;
import com.nightfall.englishnova.shared.dto.UpdateQuizOptionStrategyRequest;
import com.nightfall.englishnova.shared.enums.QuizOptionStrategy;
import com.nightfall.englishnova.shared.enums.UserStatus;
import com.nightfall.englishnova.shared.exception.ForbiddenException;
import com.nightfall.englishnova.shared.exception.UnauthorizedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String BCRYPT_PREFIX_2A = "$2a$";
    private static final String BCRYPT_PREFIX_2B = "$2b$";
    private static final String BCRYPT_PREFIX_2Y = "$2y$";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final int targetBcryptStrength;
    private final JwtTokenService jwtTokenService;
    private final UserAvatarStorageService avatarStorageService;

    public AuthServiceImpl(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            AuthServiceConfig.PasswordSecurityProperties passwordSecurityProperties,
            JwtTokenService jwtTokenService,
            UserAvatarStorageService avatarStorageService
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.targetBcryptStrength = passwordSecurityProperties == null ? 10 : passwordSecurityProperties.bcryptStrength();
        this.jwtTokenService = jwtTokenService;
        this.avatarStorageService = avatarStorageService;
    }

    @Override
    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        UserPo user = new UserPo();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setQuizOptionStrategy(QuizOptionStrategy.RANDOM.name());
        user.setStatus(UserStatus.ACTIVE.name());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw duplicateUserException(username, email);
        }

        if (user.getId() == null) {
            throw new IllegalArgumentException("Registration failed, please try again later");
        }

        String token = jwtTokenService.issueToken(user.getId(), username);
        return new AuthTokenResponse(token, toAuthUser(user));
    }

    @Override
    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        String account = request.account().trim();
        UserPo user = findUserByAccount(account);
        if (user == null || !isSupportedBcryptHash(user.getPasswordHash())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid account or password");
        }
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new ForbiddenException("Account is disabled");
        }
        refreshPasswordHashIfNeeded(user, request.password());
        String token = jwtTokenService.issueToken(user.getId(), user.getUsername());
        return new AuthTokenResponse(token, toAuthUser(user));
    }

    @Override
    @Transactional
    public void changePassword(long userId, ChangePasswordRequest request) {
        UserPo user = requireActiveUser(userId);
        if (!isSupportedBcryptHash(user.getPasswordHash())
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        userMapper.updatePasswordHash(userId, passwordEncoder.encode(request.newPassword()));
    }

    @Override
    @Transactional
    public AuthTokenResponse updateAvatar(long userId, MultipartFile file) {
        UserPo user = requireActiveUser(userId);
        String avatarUrl = avatarStorageService.store(userId, file);
        userMapper.updateProfile(userId, user.getUsername(), avatarUrl);
        user.setAvatarUrl(avatarUrl);

        String token = jwtTokenService.issueToken(user.getId(), user.getUsername());
        return new AuthTokenResponse(token, toAuthUser(user));
    }

    @Override
    public AuthUserDto getCurrentUser(long userId, String username) {
        UserPo user = userMapper.selectById(userId);
        if (user == null || !user.getUsername().equals(username)) {
            throw new UnauthorizedException("Login session is no longer valid");
        }
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new ForbiddenException("Account is disabled");
        }
        return toAuthUser(user);
    }

    @Override
    @Transactional
    public AuthTokenResponse updateProfile(long userId, UpdateProfileRequest request) {
        UserPo user = userMapper.selectById(userId);
        if (user == null) {
            throw new UnauthorizedException("Login session is no longer valid");
        }
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new ForbiddenException("Account is disabled");
        }

        String username = request.username().trim();
        if (userMapper.countByUsernameExceptId(username, userId) > 0) {
            throw new IllegalArgumentException("Username already exists");
        }

        userMapper.updateProfile(userId, username, user.getAvatarUrl());
        user.setUsername(username);

        String token = jwtTokenService.issueToken(user.getId(), user.getUsername());
        return new AuthTokenResponse(token, toAuthUser(user));
    }

    @Override
    @Transactional
    public AuthUserDto updateQuizOptionStrategy(long userId, UpdateQuizOptionStrategyRequest request) {
        UserPo user = requireActiveUser(userId);
        QuizOptionStrategy strategy = request.quizOptionStrategy() == null
                ? QuizOptionStrategy.RANDOM
                : request.quizOptionStrategy();
        userMapper.updateQuizOptionStrategy(userId, strategy.name());
        user.setQuizOptionStrategy(strategy.name());
        return toAuthUser(user);
    }

    private UserPo findUserByAccount(String account) {
        if (looksLikeEmail(account)) {
            return userMapper.findByEmail(account.toLowerCase(Locale.ROOT));
        }
        return userMapper.findByUsername(account);
    }

    private void refreshPasswordHashIfNeeded(UserPo user, String rawPassword) {
        if (!needsBcryptRehash(user.getPasswordHash())) {
            return;
        }
        String refreshedHash = passwordEncoder.encode(rawPassword);
        userMapper.updatePasswordHash(user.getId(), refreshedHash);
        user.setPasswordHash(refreshedHash);
    }

    private IllegalArgumentException duplicateUserException(String username, String email) {
        if (userMapper.countByUsername(username) > 0) {
            return new IllegalArgumentException("Username already exists");
        }
        if (userMapper.countByEmail(email) > 0) {
            return new IllegalArgumentException("Email already exists");
        }
        return new IllegalArgumentException("Username or email already exists");
    }

    private AuthUserDto toAuthUser(UserPo user) {
        return new AuthUserDto(
                user.getId(),
                user.getUsername(),
                user.getAvatarUrl(),
                resolveQuizOptionStrategy(user.getQuizOptionStrategy())
        );
    }

    private QuizOptionStrategy resolveQuizOptionStrategy(String value) {
        if (value == null || value.isBlank()) {
            return QuizOptionStrategy.RANDOM;
        }
        try {
            return QuizOptionStrategy.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return QuizOptionStrategy.RANDOM;
        }
    }

    private UserPo requireActiveUser(long userId) {
        UserPo user = userMapper.selectById(userId);
        if (user == null) {
            throw new UnauthorizedException("Login session is no longer valid");
        }
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new ForbiddenException("Account is disabled");
        }
        return user;
    }

    private boolean isSupportedBcryptHash(String passwordHash) {
        return passwordHash != null
                && (passwordHash.startsWith(BCRYPT_PREFIX_2A)
                || passwordHash.startsWith(BCRYPT_PREFIX_2B)
                || passwordHash.startsWith(BCRYPT_PREFIX_2Y));
    }

    private boolean needsBcryptRehash(String passwordHash) {
        if (!isSupportedBcryptHash(passwordHash)) {
            return false;
        }
        int storedStrength = extractBcryptStrength(passwordHash);
        return storedStrength > 0 && storedStrength != targetBcryptStrength;
    }

    private int extractBcryptStrength(String passwordHash) {
        if (passwordHash == null || passwordHash.length() < 7) {
            return -1;
        }
        try {
            return Integer.parseInt(passwordHash.substring(4, 6));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private boolean looksLikeEmail(String account) {
        return account.indexOf('@') >= 0;
    }
}
