package com.boxue.common.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/**
 * Redis 分布式锁（SETNX + Lua）
 *
 * 解决的问题：多个进程/线程同时操作同一个资源（比如同一个人同时下同一门课）。
 * 单机场景可用 synchronized（JVM 内的锁），但项目部署多个实例后，
 * 每个实例自己的 synchronized 管不着别的实例，需要一个"所有机器都看得见的锁"。
 * Redis 就是这个"大家都看得见的公告板"。
 *
 * 加锁：SET key requestId NX PX 过期毫秒
 *   - NX（Not eXists）：只有 key 不存在时才设置成功 = "板上没人，我贴上名字"
 *   - PX 过期时间：持有者挂了，贴纸也会自动清理，不会死锁
 *   - requestId：贴纸主人的编号，解锁时证明"这张是我贴的"
 * 解锁：Lua 脚本先校验 value==requestId 再删除
 *   - 防止：我的锁过期后别人抢到，我却把他贴的纸撕了（误删别人的锁）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLock {

    private final StringRedisTemplate redisTemplate;

    /**
     * 解锁 Lua 脚本（原子操作）：
     * 先 GET 比较 value 是否等于 ARGV[1]（自己的编号），相等才 DEL。
     * 为什么必须用 Lua？"比较"和"删除"是两个动作，
     * 分两条命令中间可能被别的线程插进来，导致误删别人的锁。
     */
    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] " +
            "then return redis.call('del', KEYS[1]) " +
            "else return 0 end";

    /**
     * 尝试加锁（非阻塞：抢不到立刻返回 false，不等待）
     *
     * @param key           锁的名字，如 boxue:lock:order:6:1（用户 6 买课程 1）
     * @param requestId     抢锁人自己的编号（UUID），解锁时证明所有权
     * @param expireSeconds 锁的自动过期秒数：持有者挂了也最多占这么久
     * @return true=抢到锁
     */
    public boolean tryLock(String key, String requestId, long expireSeconds) {
        // setIfAbsent = SET key value NX PX：key 不存在才设置，并带上过期时间（一步原子完成）
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, requestId, Duration.ofSeconds(expireSeconds));
        if (Boolean.TRUE.equals(ok)) {
            log.info("[分布式锁] 抢到锁 key={}, requestId={}", key, requestId);
            return true;
        }
        log.warn("[分布式锁] 没抢到锁 key={}, requestId={}", key, requestId);
        return false;
    }

    /**
     * 释放锁：Lua 校验 value==requestId 后再删，防止误删别人的锁
     */
    public void unlock(String key, String requestId) {
        Long result = redisTemplate.execute(
                new DefaultRedisScript<>(UNLOCK_LUA, Long.class), // 脚本 + 返回类型声明为 Long
                Collections.singletonList(key),                  // KEYS[1]：锁的 key
                requestId);                                      // ARGV[1]：自己的编号
        if (result != null && result == 1L) {
            log.info("[分布式锁] 释放锁成功 key={}, requestId={}", key, requestId);
        } else {
            log.warn("[分布式锁] 释放锁失败（可能已过期或不是自己持有）key={}, requestId={}", key, requestId);
        }
    }
}