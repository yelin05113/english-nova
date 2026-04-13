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

@Service
public class JwtTokenService {

    private final SecretKey secretKey;
    private final long expirationHours;

    public JwtTokenService(AuthServiceConfig.JwtProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(normalizeSecret(properties.secret()));
        this.expirationHours = properties.expirationHours() <= 0 ? 72 : properties.expirationHours();
    }

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
