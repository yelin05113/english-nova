package com.heima.englishnova.auth.service;

import com.heima.englishnova.shared.dto.AuthTokenResponse;
import com.heima.englishnova.shared.dto.AuthUserDto;
import com.heima.englishnova.shared.dto.LoginRequest;
import com.heima.englishnova.shared.dto.RegisterRequest;
import com.heima.englishnova.shared.enums.UserStatus;
import com.heima.englishnova.shared.exception.ForbiddenException;
import com.heima.englishnova.shared.exception.UnauthorizedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        ensureUnique(username, email);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO users(username, email, password_hash, status, created_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, username);
            statement.setString(2, email);
            statement.setString(3, passwordEncoder.encode(request.password()));
            statement.setString(4, UserStatus.ACTIVE.name());
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalArgumentException("注册失败，请稍后重试");
        }

        long userId = key.longValue();
        String token = jwtTokenService.issueToken(userId, username);
        return new AuthTokenResponse(token, new AuthUserDto(userId, username));
    }

    public AuthTokenResponse login(LoginRequest request) {
        UserRow user = findByAccount(request.account().trim());
        if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new UnauthorizedException("账号或密码错误");
        }
        if (!UserStatus.ACTIVE.name().equals(user.status())) {
            throw new ForbiddenException("账号已被禁用");
        }
        String token = jwtTokenService.issueToken(user.id(), user.username());
        return new AuthTokenResponse(token, new AuthUserDto(user.id(), user.username()));
    }

    private void ensureUnique(String username, String email) {
        Integer usernameCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?",
                Integer.class,
                username
        );
        if (usernameCount != null && usernameCount > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        Integer emailCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?",
                Integer.class,
                email
        );
        if (emailCount != null && emailCount > 0) {
            throw new IllegalArgumentException("邮箱已存在");
        }
    }

    private UserRow findByAccount(String account) {
        List<UserRow> users = jdbcTemplate.query(
                """
                SELECT id, username, password_hash, status
                FROM users
                WHERE username = ? OR email = ?
                LIMIT 1
                """,
                (resultSet, rowNum) -> new UserRow(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("status")
                ),
                account,
                account.toLowerCase(Locale.ROOT)
        );
        return users.isEmpty() ? null : users.get(0);
    }

    private record UserRow(
            long id,
            String username,
            String passwordHash,
            String status
    ) {
    }
}
