package com.heima.englishnova.auth.service;

import com.heima.englishnova.auth.config.AuthServiceConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 令牌服务。
 * <p>负责基于配置生成和签发 JWT 访问令牌。</p>
 */
@Service
public class JwtTokenService {

    private final SecretKey secretKey;
    private final long expirationHours;

    /**
     * 构造注入 JWT 配置属性并初始化密钥与过期时间。
     *
     * @param properties JWT 配置属性，包含密钥与过期小时数
     */
    public JwtTokenService(AuthServiceConfig.JwtProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(normalizeSecret(properties.secret()));
        this.expirationHours = properties.expirationHours() <= 0 ? 72 : properties.expirationHours();
    }

    /**
     * 签发用户 JWT 令牌。
     *
     * @param userId   用户唯一标识
     * @param username 用户名
     * @return 签发的 JWT 字符串
     */
    public String issueToken(long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 规范化 JWT 密钥字符串，确保长度不少于 32 字节。
     *
     * @param secret 原始密钥字符串
     * @return 规范化后的 UTF-8 字节数组
     */
    private byte[] normalizeSecret(String secret) {
        String raw = secret == null ? "english-nova-local-jwt-secret-must-be-32-char" : secret.trim();
        if (raw.length() >= 32) {
            return raw.getBytes(StandardCharsets.UTF_8);
        }
        StringBuilder builder = new StringBuilder(raw);
        while (builder.length() < 32) {
            builder.append('0');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }
}
