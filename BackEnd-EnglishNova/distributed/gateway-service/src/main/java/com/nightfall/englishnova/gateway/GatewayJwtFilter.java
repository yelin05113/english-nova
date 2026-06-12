package com.nightfall.englishnova.gateway;

import com.nightfall.englishnova.gateway.utools.JwtTokenUtools;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

@Component
public class GatewayJwtFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ID = "X-Auth-User-Id";
    private static final String HEADER_USERNAME = "X-Auth-Username";
    private static final String HEADER_TIMESTAMP = "X-Auth-Timestamp";
    private static final String HEADER_SIGNATURE = "X-Auth-Signature";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/auth/login",
            "/api/auth/register",
            "/auth/register",
            "/api/system/overview",
            "/system/overview",
            "/upload/images/",
            "/actuator/health"
    );
    private static final List<String> OPTIONAL_GET_PATHS = List.of(
            "/api/search/",
            "/search/",
            "/api/public-wordbooks",
            "/public-wordbooks"
    );

    private final SecretKey secretKey;
    private final GatewayInternalAuthSigner internalAuthSigner;

    public GatewayJwtFilter(
            @Value("${english-nova.jwt.secret}") String jwtSecret,
            GatewayInternalAuthSigner internalAuthSigner
    ) {
        this.secretKey = Keys.hmacShaKeyFor(JwtTokenUtools.normalizeSecret(jwtSecret));
        this.internalAuthSigner = internalAuthSigner;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String token = resolveToken(exchange.getRequest().getHeaders());
        if (token == null || token.isBlank()) {
            if (isOptional(exchange.getRequest())) {
                return chain.filter(exchange);
            }
            return unauthorized(exchange, "请先登录");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            if (userId == null || username == null) {
                return unauthorized(exchange, "无效的登录令牌");
            }

            String timestamp = internalAuthSigner.currentTimestamp();
            String signature = internalAuthSigner.sign(userId, username, timestamp);
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(HEADER_USER_ID);
                        headers.remove(HEADER_USERNAME);
                        headers.remove(HEADER_TIMESTAMP);
                        headers.remove(HEADER_SIGNATURE);
                        headers.set(HEADER_USER_ID, userId);
                        headers.set(HEADER_USERNAME, username);
                        headers.set(HEADER_TIMESTAMP, timestamp);
                        headers.set(HEADER_SIGNATURE, signature);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception exception) {
            return unauthorized(exchange, "登录已失效，请重新登录");
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean isOptional(ServerHttpRequest request) {
        if (request.getMethod() != HttpMethod.GET) {
            return false;
        }

        String path = request.getURI().getPath();
        return OPTIONAL_GET_PATHS.stream().anyMatch(path::startsWith);
    }

    private String resolveToken(HttpHeaders headers) {
        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String payload = "{\"success\":false,\"data\":null,\"message\":\""
                + message
                + "\",\"timestamp\":\""
                + OffsetDateTime.now()
                + "\"}";
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory()
                .wrap(bytes)));
    }
}
