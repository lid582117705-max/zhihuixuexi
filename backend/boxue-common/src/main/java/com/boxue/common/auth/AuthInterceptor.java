package com.boxue.common.auth;

import com.boxue.common.exception.AuthException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String TOKEN_KEY_PREFIX = "boxue:auth:";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new AuthException("请先登录");
        }

        String token = header.substring(7);
        Claims claims = jwtUtil.parse(token);
        Long userId = Long.valueOf(claims.getSubject());

        String cached = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId);
        if (cached == null || !cached.equals(token)) {
            throw new AuthException("登录已过期或已在其他地方登录");
        }

        UserContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
