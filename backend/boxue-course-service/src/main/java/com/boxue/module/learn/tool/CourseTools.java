package com.boxue.module.learn.tool;

import com.boxue.common.exception.BizException;
import com.boxue.module.course.entity.Course;
import com.boxue.module.course.service.CourseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 给 AI 的工具集：方法名、描述、参数说明会被 Spring AI 自动转成 tools JSON 声明发给模型。
 * 模型决定调用 -> Spring AI 自动执行本方法 -> 把返回值回传给模型 -> 模型组织最终回答。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseTools {

    private final CourseService courseService;
    private final ObjectMapper objectMapper;

    /** 按课程名关键词查真实课程（工具调用时模型会填 keyword 参数） */
    @Tool(name = "query_courses", description = "按课程名称关键词查询平台在线课程，返回课程 id/标题/讲师/价格/简介")
    public String queryCourses(@ToolParam(description = "课程名称关键词，例如 Java") String keyword) {
        log.info("[AI] 执行工具 query_courses 参数 keyword={}", keyword);
        List<Course> courses = courseService.searchByTitle(keyword);
        try {
            return objectMapper.writeValueAsString(courses);
        } catch (Exception e) {
            throw new BizException("课程查询结果序列化失败");
        }
    }
}