package com.boxue.common.auth;

/**
 * 用 ThreadLocal 保存「当前这次请求」的登录用户 ID。
 * 拦截器写入，业务里读取，请求结束后必须 clear，避免线程复用串号。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
