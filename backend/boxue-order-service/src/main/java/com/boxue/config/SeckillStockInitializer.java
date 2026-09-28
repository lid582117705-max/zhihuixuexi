package com.boxue.config;

import com.boxue.common.result.Result;
import com.boxue.module.course.entity.Course;
import com.boxue.module.order.feign.CourseClient;
import com.boxue.module.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时给每门课初始化 Redis 秒杀库存 10 份。
 * 课程列表通过 OpenFeign 远程调课程服务获取（不再直查课程表，服务边界自治）。
 * NX：只在第一次写入，不覆盖秒杀进行中的库存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillStockInitializer implements CommandLineRunner {

    private final CourseClient courseClient;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(String... args) {
        Result<List<Course>> result = courseClient.listCourses();
        List<Course> courses = result == null ? null : result.getData();
        if (courses == null) {
            log.warn("秒杀库存初始化：课程服务暂不可用，跳过本次初始化");
            return;
        }
        for (Course course : courses) {
            String key = SeckillService.STOCK_KEY_PREFIX + course.getId();
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "10");
            if (Boolean.TRUE.equals(ok)) {
                log.info("秒杀库存初始化 key={} = 10", key);
            }
        }
    }
}