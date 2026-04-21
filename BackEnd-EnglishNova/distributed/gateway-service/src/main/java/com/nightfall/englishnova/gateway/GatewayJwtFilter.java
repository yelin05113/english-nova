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

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/auth/login",
            "/api/auth/register",
            "/auth/register",
            "/api/system/overview",
            "/system/overview",
            "/actuator/health"
    );
    private static final List<String> OPTIONAL_TOKEN_PATHS = List.of(
            "/api/search/",
            "/search/"
    );

    private final SecretKey secretKey;

    public GatewayJwtFilter(@Value("${english-nova.jwt.secret}") String jwtSecret) {
        this.secretKey = Keys.hmacShaKeyFor(JwtTokenUtools.normalizeSecret(jwtSecret));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String token = resolveToken(exchange.getRequest().getHeaders());
        if (token == null || token.isBlank()) {
            if (isOptional(path)) {
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

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-Auth-User-Id", userId)
                    .header("X-Auth-Username", username)
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

    private boolean isOptional(String path) {
        return OPTIONAL_TOKEN_PATHS.stream().anyMatch(path::startsWith);
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
