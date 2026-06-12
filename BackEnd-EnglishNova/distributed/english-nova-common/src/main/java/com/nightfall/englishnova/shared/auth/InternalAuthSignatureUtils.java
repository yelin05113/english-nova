package com.nightfall.englishnova.shared.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public final class InternalAuthSignatureUtils {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private InternalAuthSignatureUtils() {
    }

    public static String sign(String secret, String userId, String username, String timestamp) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Internal auth secret must not be blank");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] signature = mac.doFinal(buildPayload(userId, username, timestamp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign internal auth payload", exception);
        }
    }

    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String buildPayload(String userId, String username, String timestamp) {
        return valueOrEmpty(userId) + "\n" + valueOrEmpty(username) + "\n" + valueOrEmpty(timestamp);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
