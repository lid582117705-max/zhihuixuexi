package com.boxue.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.boxue.module.course.entity.Course;
import com.boxue.module.course.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 首次启动时写入演示课程，避免 Docker 初始化 SQL 在 Windows 下中文乱码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final CourseMapper courseMapper;

    @Override
    public void run(String... args) {
        Long count = courseMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }

        List<Course> courses = List.of(
                build("Java 从入门到精通", "99.00", "夯实 Java 基础，面向实习面试", "张老师"),
                build("Spring Boot 实战", "129.00", "快速上手企业级后端开发", "李老师"),
                build("Redis 核心原理与实战", "79.00", "缓存、分布式锁、会话等核心场景", "王老师")
        );
        courses.forEach(courseMapper::insert);
        log.info("已写入演示课程 {} 门", courses.size());
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
