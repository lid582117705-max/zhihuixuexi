package com.boxue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 网关：全区唯一入口（8080）
 * - 路由：按路径转发到三个服务（lb:// 从 Nacos 负载均衡取实例）
 * - 统一鉴权：AuthGlobalFilter 验 JWT + Redis 会话，把 userId 塞进 X-User-Id 头
 */
@SpringBootApplication
public class BoxueGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoxueGatewayApplication.class, args);
    }
}