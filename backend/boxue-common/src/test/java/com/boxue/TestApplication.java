package com.boxue;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 仅用于测试：让 common 模块里的 @SpringBootTest 能启动 Spring 上下文（连真实 Redis）
 */
@SpringBootApplication(scanBasePackages = "com.boxue")
public class TestApplication {
}