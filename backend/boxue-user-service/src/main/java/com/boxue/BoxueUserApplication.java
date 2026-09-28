package com.boxue;

import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务：注册/登录/会话（端口 8081）
 * scanBasePackages = "com.boxue"：把公共模块(common)与本服务的组件都扫描进来
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.boxue")
public class BoxueUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoxueUserApplication.class, args);
    }
}