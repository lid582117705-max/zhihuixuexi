package com.boxue.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 网关版 JwtUtil：和 common 里的一致（同一密钥才能验证同一批 token）。
 * 为什么复制一份：网关是 WebFlux 不能依赖 common（内有 Spring MVC 组件），真实项目会把
 * 纯工具拆到"无 web 的基础库"，这里先用复制示意见证机制。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireHours;

    public JwtUtil(
            @Value("${boxue.jwt.secret}") String secret,
            @Value("${boxue.jwt.expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    public String createToken(Long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtException("登录已失效，请重新登录");
        }
    }
}