package com.boxue.config;

import com.boxue.common.result.Result;
import com.boxue.module.course.entity.Course;
import com.boxue.module.order.feign.CourseClient;
import com.boxue.module.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时给每门课初始化 Redis 秒杀库存。
 * 课程列表通过 OpenFeign 远程调课程服务获取（不再直查课程表，服务边界自治）。
 *
 * 库存数量来自配置 boxue.seckill.init-stock，不再写死在代码里。
 *
 * ⚠️ 用的是 setIfAbsent（不存在才写），所以：
 *   1. 服务重启不会重置库存 —— 这是有意的，否则秒杀中途重启会把库存"复活"，等于超卖；
 *   2. 改了 init-stock 想立刻生效，必须先清掉旧 key：
 *      docker exec boxue-redis redis-cli --scan --pattern "seckill:stock:*" | xargs -r docker exec -i boxue-redis redis-cli del
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillStockInitializer implements CommandLineRunner {

    private final CourseClient courseClient;
    private final StringRedisTemplate redisTemplate;

    /** 秒杀初始库存；只在该课程的 key 不存在时生效 */
    @Value("${boxue.seckill.init-stock:10}")
    private int initStock;

    @Override
    public void run(String... args) {
        if (initStock <= 0) {
            log.warn("[秒杀库存] boxue.seckill.init-stock={} 不合法（必须大于 0），跳过本次初始化", initStock);
            return;
        }
        Result<List<Course>> result = courseClient.listCourses();
        List<Course> courses = result == null ? null : result.getData();
        if (courses == null) {
            log.warn("秒杀库存初始化：课程服务暂不可用，跳过本次初始化");
            return;
        }
        for (Course course : courses) {
            String key = SeckillService.STOCK_KEY_PREFIX + course.getId();
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(initStock));
            if (Boolean.TRUE.equals(ok)) {
                log.info("[秒杀库存] 初始化 {} = {}（来自配置 boxue.seckill.init-stock）", key, initStock);
            }
        }
    }
}