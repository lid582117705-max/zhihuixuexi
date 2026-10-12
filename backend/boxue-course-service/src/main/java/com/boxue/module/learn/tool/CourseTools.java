package com.boxue.module.learn.tool;

import com.boxue.common.auth.UserContext;
import com.boxue.common.exception.AuthException;
import com.boxue.common.exception.BizException;
import com.boxue.common.result.Result;
import com.boxue.module.course.entity.Course;
import com.boxue.module.course.service.CourseService;
import com.boxue.module.learn.feign.OrderClient;
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
    private final OrderClient orderClient;
    private final ObjectMapper objectMapper;

    /** 按课程名关键词查真实课程（工具调用时模型会填 keyword 参数） */
    @Tool(name = "query_courses", description = "按课程名称关键词查询平台在线课程，返回课程 id/标题/讲师/价格/简介")
    public String queryCourses(@ToolParam(description = "课程名称关键词，例如 Java") String keyword) {
        log.info("[AI] 执行工具 query_courses 参数 keyword={}", keyword);
        List<Course> courses = courseService.searchByTitle(keyword);
        return toJson(courses);
    }

    /** 查当前用户已购买的课程：跨服务调订单服务（OpenFeign），订单数据只由订单服务自己查库 */
    @Tool(name = "list_my_courses", description = "查询当前登录用户已购买的课程列表，返回课程 id/标题/讲师/价格/简介")
    public String listMyCourses() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AuthException("请先登录");
        }
        log.info("[AI] 执行工具 list_my_courses userId={}", userId);

        // 跨服务拿"我买过的课程 id"（X-User-Id 头声明用户身份）
        Result<List<Long>> result = orderClient.myCourseIds(userId);
        List<Long> courseIds = result == null ? List.of() : result.getData();
        if (courseIds == null || courseIds.isEmpty()) {
            return "[]";
        }

        // 拿 id 批量查课程详情（复用已有 batchGet，避免循环单查的 N+1）
        List<Course> courses = courseService.batchGet(courseIds);
        return toJson(courses);
    }

    /** 把工具执行结果转成 JSON 文本（模型只读文本） */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException("课程查询结果序列化失败");
        }
    }
}