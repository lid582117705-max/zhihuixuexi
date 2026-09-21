package com.boxue.module.seckill.service;

import com.boxue.common.auth.UserContext;
import com.boxue.common.exception.AuthException;
import com.boxue.common.exception.BizException;
import com.boxue.config.RabbitConfig;
import com.boxue.module.course.entity.Course;
import com.boxue.module.course.service.CourseService;
import com.boxue.module.seckill.dto.SeckillMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 秒杀服务：Redis Lua 原子预扣库存（防超卖）→ 抢到发 MQ（削峰）→ 消费者慢慢落库
 *
 * 为什么秒杀不用分布式锁？锁让请求"排队等"，每秒吞吐上不去；
 * Lua 脚本在 Redis 单线程里原子执行，让扣库存变成"一条命令"，不用排队等锁，吞吐高得多。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    public static final String STOCK_KEY_PREFIX = "seckill:stock:";

    private final CourseService courseService;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 扣库存 Lua 脚本（原子）：
     * 库存 > 0 才 decr（减 1）并返回 1；库存 <= 0 直接返回 0。
     * Redis 单线程执行整个脚本 = 两个请求不可能同时看到同一个库存数 → 不会超卖。
     */
    private static final String DECR_STOCK_LUA =
            "if tonumber(redis.call('get', KEYS[1]) or '0') > 0 " +
            "then redis.call('decr', KEYS[1]); return 1 " +
            "else return 0 end";

    public Map<String, Object> seckill(Long courseId) {
        Long userId = requireLoginUserId();
        // 课程必须存在且上架（getById 内部校验）
        Course course = courseService.getById(courseId);

        String stockKey = STOCK_KEY_PREFIX + courseId;

        // 1) Lua 原子预扣库存：返回 1 = 扣成功（抢到了），0 = 没库存（抢光了）
        Long ok = redisTemplate.execute(
                new DefaultRedisScript<>(DECR_STOCK_LUA, Long.class),
                Collections.singletonList(stockKey));
        if (ok == null || ok == 0L) {
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
            // 3) 补偿：消息没发出去 → 把库存加回去，避免"库存扣了但订单没生成"
            redisTemplate.opsForValue().increment(stockKey);
            log.error("[秒杀] 发 MQ 失败，已回补库存 key={}", stockKey, e);
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