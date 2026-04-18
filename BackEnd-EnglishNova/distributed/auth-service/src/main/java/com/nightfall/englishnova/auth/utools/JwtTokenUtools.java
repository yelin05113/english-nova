package com.nightfall.englishnova.auth.utools;

import java.nio.charset.StandardCharsets;

public final class JwtTokenUtools {

    private static final String DEFAULT_SECRET = "english-nova-local-jwt-secret-must-be-32-char";
    private static final int MIN_SECRET_LENGTH = 32;

    private JwtTokenUtools() {
    }

    public static byte[] normalizeSecret(String secret) {
        String raw = secret == null ? DEFAULT_SECRET : secret.trim();
        if (raw.length() >= MIN_SECRET_LENGTH) {
            return raw.getBytes(StandardCharsets.UTF_8);
        }
        StringBuilder builder = new StringBuilder(raw);
        while (builder.length() < MIN_SECRET_LENGTH) {
            builder.append('0');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }
}
