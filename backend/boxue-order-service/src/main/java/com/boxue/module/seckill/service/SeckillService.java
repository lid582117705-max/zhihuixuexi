package com.boxue.module.seckill.service;

import com.boxue.common.auth.UserContext;
import com.boxue.common.exception.AuthException;
import com.boxue.common.exception.BizException;
import com.boxue.config.RabbitConfig;
import com.boxue.module.course.entity.Course;
import com.boxue.module.order.feign.CourseClient;
import com.boxue.common.result.Result;
import com.boxue.module.seckill.dto.SeckillMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 秒杀服务：Redis Lua 原子预扣库存（防超卖）→ 抢到发 MQ（削峰）→ 消费者慢慢落库
 *
 * 为什么秒杀不用分布式锁？锁让请求"排队等"，每秒吞吐上不去；
 * Lua 脚本在 Redis 单线程里原子执行，让扣库存变成"一条命令"，不用排队等锁，吞吐高得多。
 *
 * 一人一单：Lua 里先用 Set 判断「这个用户是否已经抢过」，再扣库存，两步原子完成。
 * 为什么不在数据库加 (user_id, course_id) 唯一索引兜底？秒杀是异步落单的，
 * 等消费者插入时发现重复，用户早就看到「抢购成功」了，只能删单或进死信，体验更差。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    public static final String STOCK_KEY_PREFIX = "seckill:stock:";
    /** 已抢到该课程的用户集合，用于「一人一单」判断 */
    public static final String BOUGHT_KEY_PREFIX = "seckill:bought:";

    /**
     * 已购集合的 TTL：7 天。
     * 秒杀活动结束后这个集合就没用了，不设过期的话，课程越开越多，key 会一直堆在 Redis 里。
     * 注意：每有一次新的抢购成功都会重置 TTL，所以语义是「最后一次抢购后 7 天过期」。
     */
    private static final long BOUGHT_TTL_SECONDS = 7 * 24 * 3600L;

    private final CourseClient courseClient;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 秒杀 Lua 脚本（原子）：查重 → 扣库存 → 记录已购，三步在 Redis 里一次执行完。
     *
     * KEYS[1] = 库存 key（seckill:stock:{courseId}）
     * KEYS[2] = 已购用户集合 key（seckill:bought:{courseId}）
     * ARGV[1] = userId
     * ARGV[2] = 已购集合的过期秒数
     *
     * 返回值：1 = 抢到；0 = 没库存；-1 = 这个用户已经抢过了
     *
     * 注意顺序：必须先查「抢过没」再查库存。反过来的话，已经抢过的人会把库存扣掉才发现重复。
     */
    private static final String SECKILL_LUA = """
            if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
                return -1
            end
            if tonumber(redis.call('get', KEYS[1]) or '0') > 0 then
                redis.call('decr', KEYS[1])
                redis.call('sadd', KEYS[2], ARGV[1])
                redis.call('expire', KEYS[2], ARGV[2])
                return 1
            end
            return 0
            """;

    public Map<String, Object> seckill(Long courseId) {
        Long userId = requireLoginUserId();
        // 课程必须存在且上架（getById 内部校验）
        // OpenFeign 远程调课程服务（替代 6-1 的共享库直查）
        Result<Course> courseResult = courseClient.getCourse(courseId);
        Course course = courseResult == null ? null : courseResult.getData();
        if (course == null || course.getStatus() == null || course.getStatus() != 1) {
            throw new BizException("课程不存在或已下架");
        }

        String stockKey = STOCK_KEY_PREFIX + courseId;
        String boughtKey = BOUGHT_KEY_PREFIX + courseId;

        // 1) Lua 原子操作：查重 → 扣库存 → 记录已购（三步不可分割，避免并发下重复抢）
        Long luaResult = redisTemplate.execute(
                new DefaultRedisScript<>(SECKILL_LUA, Long.class),
                List.of(stockKey, boughtKey),
                String.valueOf(userId),
                String.valueOf(BOUGHT_TTL_SECONDS));

        if (luaResult == null) {
            throw new BizException("秒杀失败，请稍后重试");
        }
        if (luaResult == -1L) {
            throw new BizException("每人限购一份，你已经抢到过了");
        }
        if (luaResult == 0L) {
            throw new BizException("手慢了，秒杀已结束");
        }
        log.info("[秒杀] 用户 {} 预扣库存成功，剩余={}", userId, redisTemplate.opsForValue().get(stockKey));

        // 2) 抢到 → 发 MQ 消息，让消费者慢慢落库（削峰填谷：高峰变平地）
        SeckillMessage message = new SeckillMessage(UUID.randomUUID().toString(), userId, courseId);
        try {
            CorrelationData cd = new CorrelationData(UUID.randomUUID().toString());
            rabbitTemplate.convertAndSend("", RabbitConfig.SECKILL_QUEUE,
                    objectMapper.writeValueAsString(message), cd);
            log.info("[秒杀] 用户 {} 抢到课程 {}，已发 MQ 待落单", userId, courseId);
        } catch (Exception e) {
            // 3) 补偿：消息没发出去 → 库存加回去，同时把用户从「已购」集合移除。
            //    少了 SREM 的话，用户会被标记成"抢过"，却既没有订单也不能再抢，名额被永久占用。
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForSet().remove(boughtKey, String.valueOf(userId));
            log.error("[秒杀] 发 MQ 失败，已回补库存并释放名额 key={} userId={}", stockKey, userId, e);
            throw new BizException("秒杀人数过多，请重试");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "抢购成功，订单正在生成");
        result.put("courseId", courseId);
        return result;
    }

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AuthException("请先登录");
        }
        return userId;
    }
}