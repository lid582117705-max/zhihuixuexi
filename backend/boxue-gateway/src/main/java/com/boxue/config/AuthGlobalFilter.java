package com.boxue.config;

import com.boxue.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局鉴权过滤器（网关统一鉴权）：
 * 1. 白名单（登录/注册/健康/公开课程）直接放行
 * 2. 其他请求：验 JWT 签名+过期 → 验 Redis 会话白名单
 * 3. 通过后把 userId 塞进 X-User-Id 头转发给下游服务；下游不再验 token
 */
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    private static final List<String> WHITE_LIST = List.of(
            "/api/health",
            "/api/users/register",
            "/api/users/login",
            "/api/courses",
            "/doc.html",
            "/webjars",
            "/v3/api-docs",
            "/swagger-ui",
            "/favicon.ico"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单 + 跨域预检：直接放行
        if (isWhite(path) || path.contains("/v3/api-docs") || "OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }

        // 1) 拿 Authorization 头
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        try {
            String token = auth.substring(7);
            // 2) JWT 签名/过期校验
            Claims claims = jwtUtil.parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            // 3) Redis 会话白名单（和原来 AuthInterceptor 完全一致：支持退出/顶号）
            String cached = redisTemplate.opsForValue().get("boxue:auth:" + userId);
            if (cached == null || !cached.equals(token)) {
                return unauthorized(exchange);
            }
            // 4) 把 userId 塞进转发头
            ServerHttpRequest newRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .build();
            return chain.filter(exchange.mutate().request(newRequest).build());
        } catch (Exception e) {
            return unauthorized(exchange);
        }
    }

    /** 路径命中白名单（等于该路径 或 是该路径的子路径） */
    private boolean isWhite(String path) {
        for (String w : WHITE_LIST) {
            if (path.equals(w) || path.startsWith(w + "/")) {
                return true;
            }
        }
        return false;
    }

    /** 返回统一 401 JSON */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"请先登录\",\"data\":null}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /** 负数 = 尽早执行（比普通路由过滤器更先跑） */
    @Override
    public int getOrder() {
        return -100;
    }
}