package com.nightfall.englishnova.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class GatewayInternalAuthSignatureUtils {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private GatewayInternalAuthSignatureUtils() {
    }

    static String sign(String secret, String userId, String username, String timestamp) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Internal auth secret must not be blank");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] payload = (valueOrEmpty(userId) + "\n" + valueOrEmpty(username) + "\n" + valueOrEmpty(timestamp))
                    .getBytes(StandardCharsets.UTF_8);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign internal auth payload", exception);
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
