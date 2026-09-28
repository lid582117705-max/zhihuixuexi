package com.boxue.common.lock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RedisLock 的单元测试（@SpringBootTest：拉起完整 Spring 环境，连真实的 Redis 操作）
 *
 * 三个用例对应锁的三个核心要求：
 * 1. 正常加锁/解锁：抢得到；没释放前别人抢不到；释放后别人能抢到
 * 2. 自动过期：持锁者不释放，锁到期后别人也能抢到（不会死锁）
 * 3. 并发互斥：多个请求"同时"抢同一把锁，只有 1 个能赢
 */
@SpringBootTest
class RedisLockTest {

    @Autowired
    private RedisLock redisLock;

    @Test
    void tryLockAndUnlock() {
        String key = "test:lock:" + UUID.randomUUID();      // 每次用随机 key，避免污染
        String requestId = UUID.randomUUID().toString();    // 主人编号

        // 1) 第一次能抢到
        assertTrue(redisLock.tryLock(key, requestId, 5));

        // 2) 没释放之前，别人抢不到（用不同的 requestId = 另一个人）
        assertFalse(redisLock.tryLock(key, UUID.randomUUID().toString(), 5));

        // 3) 释放之后，别人能抢到
        redisLock.unlock(key, requestId);
        assertTrue(redisLock.tryLock(key, UUID.randomUUID().toString(), 5));
    }

    @Test
    void lockExpiresAfterTimeout() throws InterruptedException {
        String key = "test:expire:" + UUID.randomUUID();

        // 锁只给 1 秒过期时间
        assertTrue(redisLock.tryLock(key, UUID.randomUUID().toString(), 1));

        // 持锁者"挂掉"：不释放。等 1.2 秒，锁应该自动消失，别人能抢到（验证不会死锁）
        Thread.sleep(1200);
        assertTrue(redisLock.tryLock(key, UUID.randomUUID().toString(), 5));
    }

    @Test
    void concurrentOnlyOneWins() throws InterruptedException {
        String key = "test:concurrent:" + UUID.randomUUID();
        int threads = 10;                                // 10 个人同时抢
        CountDownLatch ready = new CountDownLatch(threads); // 记录"我准备好了"
        CountDownLatch start = new CountDownLatch(1);       // 发令枪
        AtomicInteger successCount = new AtomicInteger(0);  // 抢到的人数（线程安全计数器）

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                ready.countDown();      // 1) 向 ready 报告：我准备好了
                try {
                    start.await();      // 2) 等发令枪响
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                // 3) 响枪后 10 个线程"同时"去抢同一把锁
                if (redisLock.tryLock(key, UUID.randomUUID().toString(), 5)) {
                    successCount.incrementAndGet(); // 抢到的人 +1
                }
            }).start();
        }

        ready.await();    // 主线程等所有参赛者就位
        start.countDown(); // 开枪！
        Thread.sleep(500); // 留点时间让 10 个线程跑完
        assertEquals(1, successCount.get()); // 关键断言：只有 1 个成功
    }
}