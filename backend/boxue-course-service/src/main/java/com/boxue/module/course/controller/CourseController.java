package com.boxue.module.course.controller;

import com.boxue.common.result.Result;
import com.boxue.module.course.entity.Course;
import com.boxue.module.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public Result<List<Course>> list() {
        return Result.ok(courseService.listOnline());
    }

    @GetMapping("/{id}")
    public Result<Course> detail(@PathVariable Long id) {
        return Result.ok(courseService.getById(id));
    }

    /** 批量查课程（订单服务"我的课程"远程调用，服务间内部接口） */
    @PostMapping("/batch")
    public Result<List<Course>> batch(@RequestBody List<Long> ids) {
        return Result.ok(courseService.batchGet(ids));
    }
}