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

/**
 * 认证业务服务。
 * <p>处理用户注册、登录校验及 JWT 令牌发放。</p>
 */
@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    /**
     * 构造注入所需依赖。
     *
     * @param jdbcTemplate     Spring JDBC 模板，用于数据库操作
     * @param passwordEncoder  密码编码器，用于密码加密与校验
     * @param jwtTokenService  JWT 令牌服务，用于签发登录令牌
     */
    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * 用户注册。
     * <p>校验用户名与邮箱唯一性后，将用户信息写入数据库并签发 JWT 令牌。</p>
     *
     * @param request 注册请求参数
     * @return 包含 JWT 令牌及用户信息的响应
     * @throws IllegalArgumentException 当用户名或邮箱已存在，或主键生成失败时抛出
     */
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

    /**
     * 用户登录。
     * <p>根据账号查询用户，校验密码与状态后签发 JWT 令牌。</p>
     *
     * @param request 登录请求参数
     * @return 包含 JWT 令牌及用户信息的响应
     * @throws UnauthorizedException 当账号不存在或密码错误时抛出
     * @throws ForbiddenException    当账号状态非 ACTIVE 时抛出
     */
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

    /**
     * 确保用户名与邮箱在系统中唯一。
     *
     * @param username 待校验的用户名
     * @param email    待校验的邮箱
     * @throws IllegalArgumentException 当用户名或邮箱已存在时抛出
     */
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

    /**
     * 根据账号（用户名或邮箱）查询用户信息。
     *
     * @param account 账号（用户名或邮箱）
     * @return 匹配的用户记录，若不存在则返回 {@code null}
     */
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

    /**
     * 用户数据库记录内部 DTO。
     *
     * @param id           用户 ID
     * @param username     用户名
     * @param passwordHash 密码哈希值
     * @param status       用户状态
     */
    private record UserRow(
            long id,
            String username,
            String passwordHash,
            String status
    ) {
    }
}
