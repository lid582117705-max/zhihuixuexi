package com.boxue.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置：生产级分布式锁/分布式工具的入口。
 * 用法：注入 RedissonClient → getLock(key) → lock/unlock
 * 对比手写版 RedisLock：Redisson 提供可重入、看门狗自动续期、断线自动释放等能力。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    /** destroyMethod = "shutdown"：应用关闭时自动释放连接池 */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 单机模式：redis://host:port（本地就是 docker 里的 127.0.0.1:6379）
        config.useSingleServer().setAddress("redis://" + host + ":" + port);
        return Redisson.create(config);
    }
}