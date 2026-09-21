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

import java.math.BigDecimal;
import java.util.List;

/**
 * 启动时初始化演示数据：
 * 1. 课程不存在则写入演示课程（避免 Docker SQL 中文乱码）
 * 2. 给每门课初始化 Redis 秒杀库存 10 份（NX：只在第一次写入，不覆盖进行中的秒杀）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final CourseMapper courseMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(String... args) {
        // 1) 演示课程：没有才插入
        Long count = courseMapper.selectCount(new LambdaQueryWrapper<>());
        if (count == null || count == 0) {
            List<Course> courses = List.of(
                    build("Java 从入门到精通", "99.00", "夯实 Java 基础，面向实习面试", "张老师"),
                    build("Spring Boot 实战", "129.00", "快速上手企业级后端开发", "李老师"),
                    build("Redis 核心原理与实战", "79.00", "缓存、分布式锁、会话等核心场景", "王老师")
            );
            courses.forEach(courseMapper::insert);
            log.info("已写入演示课程 {} 门", courses.size());
        }

        // 2) 秒杀库存：每门课初始 10 份（NX 幂等，秒杀进行中不会被重置）
        for (Course course : courseMapper.selectList(new LambdaQueryWrapper<>())) {
            String key = SeckillService.STOCK_KEY_PREFIX + course.getId();
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "10");
            if (Boolean.TRUE.equals(ok)) {
                log.info("秒杀库存初始化 key={} = 10", key);
            }
        }
    }

    private Course build(String title, String price, String description, String teacherName) {
        Course course = new Course();
        course.setTitle(title);
        course.setPrice(new BigDecimal(price));
        course.setDescription(description);
        course.setTeacherName(teacherName);
        course.setStatus(1);
        return course;
    }
}