package com.nightfall.englishnova.gateway;

import com.nightfall.englishnova.gateway.utools.JwtTokenUtools;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayJwtFilterTest {

    private static final String JWT_SECRET = "english-nova-test-jwt-secret-123456";
    private static final String INTERNAL_SECRET = "english-nova-test-internal-secret";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-09T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void filterOverridesSpoofedIdentityHeadersWithGatewaySignedHeaders() {
        GatewayInternalAuthSigner signer = new GatewayInternalAuthSigner(
                INTERNAL_SECRET,
                FIXED_CLOCK
        );
        GatewayJwtFilter filter = new GatewayJwtFilter(JWT_SECRET, signer);
        String token = Jwts.builder()
                .subject("42")
                .claim("username", "alice")
                .signWith(Keys.hmacShaKeyFor(JwtTokenUtools.normalizeSecret(JWT_SECRET)))
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quiz/sessions/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Auth-User-Id", "999")
                .header("X-Auth-Username", "mallory")
                .header("X-Auth-Timestamp", "1")
                .header("X-Auth-Signature", "bad")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerHttpRequest> forwardedRequest = new AtomicReference<>();
        GatewayFilterChain chain = gatewayExchange -> {
            forwardedRequest.set(gatewayExchange.getRequest());
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        ServerHttpRequest result = forwardedRequest.get();
        String expectedTimestamp = Long.toString(FIXED_CLOCK.instant().getEpochSecond());
        assertEquals("42", result.getHeaders().getFirst("X-Auth-User-Id"));
        assertEquals("alice", result.getHeaders().getFirst("X-Auth-Username"));
        assertEquals(expectedTimestamp, result.getHeaders().getFirst("X-Auth-Timestamp"));
        assertEquals(
                GatewayInternalAuthSignatureUtils.sign(INTERNAL_SECRET, "42", "alice", expectedTimestamp),
                result.getHeaders().getFirst("X-Auth-Signature")
        );
    }
}
