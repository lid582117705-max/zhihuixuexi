package com.boxue.module.course.controller;

import com.boxue.common.result.Result;
import com.boxue.module.course.entity.Course;
import com.boxue.module.course.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "课程服务-课程", description = "课程列表 / 详情 / 批量查询（Redis 缓存）")
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "课程列表", description = "Cache Aside 缓存")
    @GetMapping
    public Result<List<Course>> list() {
        return Result.ok(courseService.listOnline());
    }

    @Operation(summary = "课程详情", description = "Cache Aside 缓存")
    @Operation(summary = "课程列表", description = "Cache Aside 缓存")
    @GetMapping("/{id}")
    public Result<Course> detail(@PathVariable Long id) {
        return Result.ok(courseService.getById(id));
    }

    /** 批量查课程（订单服务"我的课程"远程调用，服务间内部接口） */
    @Operation(summary = "批量查课程（内部接口）", description = "订单服务 OpenFeign 远程调用")
    @PostMapping("/batch")
    public Result<List<Course>> batch(@RequestBody List<Long> ids) {
        return Result.ok(courseService.batchGet(ids));
    }
}