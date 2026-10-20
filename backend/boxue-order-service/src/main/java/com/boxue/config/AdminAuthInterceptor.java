package com.boxue.config;

import com.boxue.common.auth.UserContext;
import com.boxue.common.exception.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员权限拦截器（最小实现版）
 *
 * 为什么需要它：
 * 死信管理接口能"重放消息"——往生产队列投消息，属于高危写操作。
 * 之前只校验"有没有登录"，等于任何注册用户都能操作，是典型的越权访问
 * （Broken Access Control，OWASP Top 10 常年第一）。
 *
 * 实现方式：
 * 配置里维护一份管理员 userId 白名单（boxue.admin.user-ids），
 * 请求进来时比对 UserContext 里的 userId。
 *
 * 为什么用白名单而不是角色表：
 * 项目目前没有 RBAC（user 表没有 role 字段），白名单是"最小成本堵漏"。
 * 生产应改为：user 表加 role → 登录时写进 JWT → 网关透传 X-User-Role →
 * 服务端用 Spring Security 的 @PreAuthorize("hasRole('ADMIN')") 校验。
 *
 * 注意：前端隐藏入口不是安全措施，真正的防线必须在服务端。
 */
@Slf4j
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    /** 逗号分隔的管理员 userId，例如 "1,2"；不配置 = 谁都不放行 */
    @Value("${boxue.admin.user-ids:}")
    private String adminUserIds;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 浏览器跨域预检不带业务头，直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AuthException("请先登录");
        }
        if (!isAdmin(userId)) {
            log.warn("[权限] 用户 {} 尝试访问管理接口 {}，已拒绝", userId, request.getRequestURI());
            throw new AuthException("无权访问：需要管理员权限");
        }
        return true;
    }

    /**
     * 白名单比对。
     * 没配置时返回 false（fail-closed）：宁可把管理员拦在外面，也不能让所有人进来。
     */
    private boolean isAdmin(Long userId) {
        if (!StringUtils.hasText(adminUserIds)) {
            return false;
        }
        String target = String.valueOf(userId);
        for (String id : adminUserIds.split(",")) {
            if (target.equals(id.trim())) {
                return true;
            }
        }
        return false;
    }
}
