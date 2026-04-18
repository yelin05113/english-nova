package com.nightfall.englishnova.auth.service.impl;

import com.nightfall.englishnova.auth.config.AuthServiceConfig;
import com.nightfall.englishnova.auth.service.JwtTokenService;
import com.nightfall.englishnova.auth.utools.JwtTokenUtools;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtTokenServiceImpl implements JwtTokenService {

    private final SecretKey secretKey;
    private final long expirationHours;

    public JwtTokenServiceImpl(AuthServiceConfig.JwtProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(JwtTokenUtools.normalizeSecret(properties.secret()));
        this.expirationHours = properties.expirationHours() <= 0 ? 72 : properties.expirationHours();
    }

    @Override
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
}
