package com.heima.englishnova.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Map;

/**
 * 网关全局 JWT 鉴权过滤器。
 * <p>对所有经过网关的请求进行 JWT 校验，公开路径与可选鉴权路径除外；
 * 校验通过后将用户信息透传给下游服务。</p>
 */
@Component
public class GatewayJwtFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/system/overview",
            "/actuator/health"
    );
    private static final List<String> OPTIONAL_TOKEN_PATHS = List.of(
            "/api/search/"
    );

    private final SecretKey secretKey;

    /**
     * 构造注入 JWT 密钥并初始化签名密钥。
     *
     * @param jwtSecret 配置项中的 JWT 密钥
     */
    public GatewayJwtFilter(
            @Value("${english-nova.jwt.secret}") String jwtSecret
    ) {
        this.secretKey = Keys.hmacShaKeyFor(normalizeSecret(jwtSecret));
    }

    /**
     * 执行 JWT 校验过滤逻辑。
     *
     * @param exchange 当前请求-响应交换对象
     * @param chain    过滤器链，用于继续后续处理
     * @return 表示异步处理完成的 Mono
     */
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

    /**
     * 返回过滤器执行顺序，数值越小优先级越高。
     *
     * @return 过滤器顺序值
     */
    @Override
    public int getOrder() {
        return -100;
    }

    /**
     * 判断请求路径是否为公开访问路径。
     *
     * @param path 请求路径
     * @return 若为公开路径则返回 {@code true}
     */
    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * 判断请求路径是否允许不带令牌访问。
     *
     * @param path 请求路径
     * @return 若为可选鉴权路径则返回 {@code true}
     */
    private boolean isOptional(String path) {
        return OPTIONAL_TOKEN_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * 从请求头中解析 Bearer 令牌。
     *
     * @param headers HTTP 请求头
     * @return 解析出的 JWT 字符串，若不存在或格式错误则返回 {@code null}
     */
    private String resolveToken(HttpHeaders headers) {
        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }

    /**
     * 构造并返回 401 未授权响应。
     *
     * @param exchange 当前请求-响应交换对象
     * @param message  错误提示信息
     * @return 表示响应写入完成的 Mono
     */
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

    /**
     * 规范化 JWT 密钥字符串，确保长度不少于 32 字节。
     *
     * @param secret 原始密钥字符串
     * @return 规范化后的 UTF-8 字节数组
     */
    private byte[] normalizeSecret(String secret) {
        String raw = secret == null ? "english-nova-local-jwt-secret-must-be-32-char" : secret.trim();
        if (raw.length() >= 32) {
            return raw.getBytes(StandardCharsets.UTF_8);
        }
        StringBuilder builder = new StringBuilder(raw);
        while (builder.length() < 32) {
            builder.append('0');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }
}
