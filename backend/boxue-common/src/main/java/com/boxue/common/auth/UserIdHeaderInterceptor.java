package com.boxue.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 6-3 起：网关统一鉴权，服务端不再验 token，只信网关转发的 X-User-Id 头。
 * 本拦截器负责把该头读出来放进 UserContext，业务代码继续用 UserContext.getUserId() 无感知。
 * 注意：它不拦截任何人（鉴权在网关），直连服务时没头 = UserContext 为空，业务层兜底"请先登录"。
 */
@Component
public class UserIdHeaderInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            UserContext.setUserId(Long.valueOf(userId));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear(); // 用完必清：线程池复用防串号
    }
}