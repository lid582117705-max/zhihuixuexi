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
 * 空库时写入演示课程（避免 Docker SQL 中文乱码的老问题）
 *
 * 只在 course 表为空时执行，已有数据的库不会重复插入。
 * 老数据卷要补数据，用 docker/mysql/upgrade-course-fields.sql。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoCourseInitializer implements CommandLineRunner {

    private final CourseMapper courseMapper;

    @Override
    public void run(String... args) {
        Long count = courseMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }
        List<Course> courses = List.of(
                build("Java 从入门到精通", "99.00", "夯实 Java 基础，面向实习面试", "张老师", "后端开发", 40, 5120),
                build("Spring Boot 实战", "129.00", "快速上手企业级后端开发", "李老师", "后端开发", 36, 4380),
                build("Redis 核心原理与实战", "79.00", "缓存、分布式锁、会话等核心场景", "王老师", "架构与中间件", 30, 3020),
                build("MySQL 索引与调优", "89.00", "执行计划解读、索引下推、慢查询治理", "赵老师", "数据库", 32, 4210),
                build("高并发系统设计", "199.00", "限流、降级、缓存、异步化的系统方法论", "陈老师", "架构与中间件", 48, 2890),
                build("Vue3 组合式 API 实战", "109.00", "script setup、响应式原理与组件设计", "刘老师", "前端开发", 36, 3650),
                build("TypeScript 类型体操", "119.00", "泛型、条件类型、类型推导进阶", "孙老师", "前端开发", 28, 1980),
                build("Spring Cloud 微服务实战", "219.00", "注册中心、网关、Feign、配置中心全链路", "李老师", "架构与中间件", 52, 2460),
                build("RabbitMQ 消息可靠性", "139.00", "确认机制、死信队列、幂等与顺序性", "王老师", "架构与中间件", 30, 1720),
                build("JVM 性能调优", "169.00", "内存模型、GC 调参、线上问题排查", "周老师", "后端开发", 40, 2100),
                build("MyBatis-Plus 高效开发", "89.00", "条件构造器、分页插件、代码生成器", "张老师", "后端开发", 24, 3150),
                build("Python 数据分析入门", "99.00", "Pandas、NumPy 与可视化实战", "吴老师", "AI 应用", 30, 2780),
                build("大模型应用开发实战", "259.00", "Prompt 工程、工具调用、Agent 编排", "郑老师", "AI 应用", 45, 1560),
                build("RAG 检索增强生成", "189.00", "向量化、召回、重排与效果评估", "郑老师", "AI 应用", 32, 980),
                build("Docker 与 Kubernetes 入门", "149.00", "镜像构建、编排部署、服务暴露", "陈老师", "架构与中间件", 38, 2240),
                build("前端工程化与 Vite", "129.00", "构建原理、插件机制、按需加载", "刘老师", "前端开发", 26, 1430),
                build("SQL 优化实战", "109.00", "慢 SQL 定位、执行计划、分页优化", "赵老师", "数据库", 28, 2670),
                build("分布式事务与一致性", "179.00", "2PC、TCC、可靠消息与最终一致", "周老师", "架构与中间件", 34, 1120),
                build("React 18 核心原理", "139.00", "Fiber、并发渲染、Hooks 实现原理", "孙老师", "前端开发", 30, 1890),
                build("Elasticsearch 搜索实战", "159.00", "倒排索引、分词、聚合与相关性打分", "吴老师", "数据库", 32, 860)
        );
        courses.forEach(courseMapper::insert);
        log.info("已写入演示课程 {} 门", courses.size());
    }

    private Course build(String title, String price, String description, String teacherName,
                         String category, int lessonCount, int studentCount) {
        Course course = new Course();
        course.setTitle(title);
        course.setPrice(new BigDecimal(price));
        course.setDescription(description);
        course.setTeacherName(teacherName);
        course.setCategory(category);
        course.setLessonCount(lessonCount);
        course.setStudentCount(studentCount);
        course.setStatus(1);
        return course;
    }
}
