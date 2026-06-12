package com.nightfall.englishnova.gateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class GatewayInternalAuthSigner {

    private final String internalAuthSecret;
    private final Clock clock;

    @Autowired
    public GatewayInternalAuthSigner(
            @Value("${english-nova.gateway.internal-auth-secret:english-nova-internal-auth-secret-change-me}") String internalAuthSecret
    ) {
        this(internalAuthSecret, Clock.systemUTC());
    }

    GatewayInternalAuthSigner(String internalAuthSecret, Clock clock) {
        if (internalAuthSecret == null || internalAuthSecret.isBlank()) {
            throw new IllegalArgumentException("Internal auth secret must not be blank");
        }
        this.internalAuthSecret = internalAuthSecret;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public String currentTimestamp() {
        return Long.toString(clock.instant().getEpochSecond());
    }

    public String sign(String userId, String username, String timestamp) {
        return GatewayInternalAuthSignatureUtils.sign(internalAuthSecret, userId, username, timestamp);
    }
}
