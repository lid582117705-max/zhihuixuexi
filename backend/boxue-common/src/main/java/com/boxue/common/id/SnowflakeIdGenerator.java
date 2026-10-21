package com.boxue.common.id;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 雪花 ID 生成器（简化手写版）
 *
 * ⚠️ 当前状态：订单号已改用 Redis INCR（见 OrderService#generateOrderNo），本类保留作为
 *    雪花算法的原理参考与备用方案，Spring 仍会创建这个 bean，但不参与业务。
 *    若要重新启用，把 OrderService 里的 redisTemplate.increment 换回 nextId() 即可。
 *
 * 一个 long 是 64 位，切成三段拼起来：
 *
 *   41 位时间戳（毫秒） | 10 位机器号 | 12 位序列号
 *
 *   - 时间戳：现在距 2025-01-01 过了多少毫秒，41 位够用约 69 年
 *   - 机器号：哪台机器发的，10 位最多 1024 台，多实例部署时每台配不同值
 *   - 序列号：这一毫秒内的第几个，12 位最多 4096 个，用完了就等下一毫秒
 *
 * 为什么不会重复：三段同时相同才会撞。同一台机器在同一毫秒内，
 * 靠序列号递增区分；序列号用完就等到下一毫秒，时间戳又变了。
 */
@Component
public class SnowflakeIdGenerator {

    /** 起始时间：2025-01-01 00:00:00（东八区）。时间戳从这一刻开始算，不用 1970 */
    private static final long START_TIME = 1735660800000L;

    /** 机器号占 10 位，最多 1024 台 */
    private static final int WORKER_BITS = 10;
    /** 序列号占 12 位，每毫秒最多 4096 个 */
    private static final int SEQUENCE_BITS = 12;

    /** 序列号上限 = 2^12 = 4096（到达就等下一毫秒） */
    private static final long MAX_SEQUENCE = 1L << SEQUENCE_BITS;
    /** 机器号上限 = 2^10 = 1024（合法值 0 ~ 1023） */
    private static final long MAX_WORKER = 1L << WORKER_BITS;

    /** 机器号要左移 12 位，把低 12 位让给序列号 */
    private static final int WORKER_SHIFT = SEQUENCE_BITS;
    /** 时间戳要左移 22 位，把低 22 位让给机器号和序列号 */
    private static final int TIME_SHIFT = SEQUENCE_BITS + WORKER_BITS;

    private final long workerId;

    /** 上次发号用的毫秒时间戳，-1 表示还没发过 */
    private long lastTime = -1L;
    /** 当前这一毫秒已经发到第几个 */
    private long sequence = 0L;

    public SnowflakeIdGenerator(@Value("${boxue.snowflake.worker-id:1}") long workerId) {
        if (workerId < 0 || workerId >= MAX_WORKER) {
            throw new IllegalArgumentException("workerId 必须在 0 ~ " + (MAX_WORKER - 1) + " 之间");
        }
        this.workerId = workerId;
    }

    /**
     * 生成下一个 ID。
     * synchronized：lastTime 和 sequence 是共享状态，两个线程同时改会发出重复 ID。
     */
    public synchronized long nextId() {
        long now = System.currentTimeMillis();

        // 时钟被往回调了（NTP 对时、人工改时间）。继续发号必然重复，直接拒绝。
        if (now < lastTime) {
            throw new IllegalStateException("系统时钟回拨 " + (lastTime - now) + "ms，拒绝生成 ID");
        }

        if (now == lastTime) {
            // 同一毫秒：序列号 +1
            sequence++;
            if (sequence >= MAX_SEQUENCE) {
                // 这一毫秒的 4096 个号用完了，等到下一毫秒重新开始
                now = waitNextMillis(lastTime);
                sequence = 0;
            }
        } else {
            // 进入新的一毫秒：序列号归零
            sequence = 0;
        }

        lastTime = now;

        // 三段拼起来：因为各占的二进制位不重叠，用 | 拼等价于相加
        return ((now - START_TIME) << TIME_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    /** 自旋等待，直到系统时间超过 target（毫秒） */
    private long waitNextMillis(long target) {
        long now = System.currentTimeMillis();
        while (now <= target) {
            now = System.currentTimeMillis();
        }
        return now;
    }
}
