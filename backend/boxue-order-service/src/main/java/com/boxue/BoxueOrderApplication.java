package com.boxue;

import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 订单服务：下单/支付/关单/我的课程/秒杀（端口 8083）
 * 骨架阶段：业务代码下一轮搬入
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.boxue")
public class BoxueOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoxueOrderApplication.class, args);
    }
}