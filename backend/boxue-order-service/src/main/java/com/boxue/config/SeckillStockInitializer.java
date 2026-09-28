package com.boxue.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.boxue.module.course.entity.Course;
import com.boxue.module.course.mapper.CourseMapper;
import com.boxue.module.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时给每门课初始化 Redis 秒杀库存 10 份。
 * NX：只在第一次写入，不覆盖秒杀进行中的库存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillStockInitializer implements CommandLineRunner {

    private final CourseMapper courseMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(String... args) {
        for (Course course : courseMapper.selectList(new LambdaQueryWrapper<>())) {
            String key = SeckillService.STOCK_KEY_PREFIX + course.getId();
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "10");
            if (Boolean.TRUE.equals(ok)) {
                log.info("秒杀库存初始化 key={} = 10", key);
            }
        }
    }
}