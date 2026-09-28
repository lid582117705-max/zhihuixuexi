package com.boxue.module.order.feign;

import com.boxue.common.result.Result;
import com.boxue.module.course.entity.Course;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 调用课程服务（lb://boxue-course-service）的声明式客户端。
 * OpenFeign 启动时自动生成实现：方法调用 = 真实 HTTP 调用（从 Nacos 找实例 + 负载均衡）。
 */
@FeignClient(name = "boxue-course-service")
public interface CourseClient {

    /** 对应 course-service 公开接口 GET /api/courses/{id}（无需 token） */
    @GetMapping("/api/courses/{id}")
    Result<Course> getCourse(@PathVariable("id") Long id);

    /** 对应 course-service 公开接口 GET /api/courses（秒杀库存初始化用） */
    @GetMapping("/api/courses")
    Result<List<Course>> listCourses();

    /** 对应 course-service 批量接口 POST /api/courses/batch（我的课程页用） */
    @PostMapping("/api/courses/batch")
    Result<List<Course>> batchGetCourses(@RequestBody List<Long> ids);
}