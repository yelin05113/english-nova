package com.nightfall.englishnova.auth.utools;

import java.nio.charset.StandardCharsets;

public final class JwtTokenUtools {

    private static final int MIN_SECRET_LENGTH = 32;

    private JwtTokenUtools() {
    }

    public static byte[] normalizeSecret(String secret) {
        String raw = secret == null ? "" : secret.trim();
        if (raw.isEmpty()) {
            throw new IllegalStateException("JWT_SECRET must be configured.");
        }
        if (raw.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters.");
        }
        return raw.getBytes(StandardCharsets.UTF_8);
    }
}
