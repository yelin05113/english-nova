package com.nightfall.englishnova.shared.auth;

import com.nightfall.englishnova.shared.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class InternalAuthVerifier {

    private final String internalAuthSecret;
    private final long maxSkewSeconds;
    private final Clock clock;

    @Autowired
    public InternalAuthVerifier(
            @Value("${english-nova.gateway.internal-auth-secret:english-nova-internal-auth-secret-change-me}") String internalAuthSecret,
            @Value("${english-nova.gateway.internal-auth-max-skew-seconds:60}") long maxSkewSeconds
    ) {
        this(internalAuthSecret, maxSkewSeconds, Clock.systemUTC());
    }

    InternalAuthVerifier(String internalAuthSecret, long maxSkewSeconds, Clock clock) {
        if (internalAuthSecret == null || internalAuthSecret.isBlank()) {
            throw new IllegalArgumentException("Internal auth secret must not be blank");
        }
        this.internalAuthSecret = internalAuthSecret;
        this.maxSkewSeconds = Math.max(1L, maxSkewSeconds);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public CurrentUser require(HttpServletRequest request) {
        if (isMissingAllHeaders(request)) {
            throw new UnauthorizedException("请先登录");
        }
        CurrentUser user = optional(request);
        if (user == null) {
            throw new UnauthorizedException("无效的登录上下文");
        }
        return user;
    }

    public CurrentUser optional(HttpServletRequest request) {
        String rawUserId = request.getHeader(AuthHeaders.USER_ID);
        String username = request.getHeader(AuthHeaders.USERNAME);
        String timestamp = request.getHeader(AuthHeaders.TIMESTAMP);
        String signature = request.getHeader(AuthHeaders.SIGNATURE);
        if (isBlank(rawUserId) || isBlank(username) || isBlank(timestamp) || isBlank(signature)) {
            return null;
        }

        long timestampSeconds;
        long nowSeconds = clock.instant().getEpochSecond();
        try {
            timestampSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return null;
        }
        if (Math.abs(nowSeconds - timestampSeconds) > maxSkewSeconds) {
            return null;
        }

        String expectedSignature = InternalAuthSignatureUtils.sign(internalAuthSecret, rawUserId, username, timestamp);
        if (!InternalAuthSignatureUtils.matches(expectedSignature, signature)) {
            return null;
        }

        try {
            return new CurrentUser(Long.parseLong(rawUserId), username);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isMissingAllHeaders(HttpServletRequest request) {
        return isBlank(request.getHeader(AuthHeaders.USER_ID))
                && isBlank(request.getHeader(AuthHeaders.USERNAME))
                && isBlank(request.getHeader(AuthHeaders.TIMESTAMP))
                && isBlank(request.getHeader(AuthHeaders.SIGNATURE));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
