package com.boxue;

import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 课程服务：课程列表/详情 + Redis 缓存 + 学习对话（端口 8082）
 * 骨架阶段：业务代码下一轮搬入
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.boxue")
public class BoxueCourseApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoxueCourseApplication.class, args);
    }
}