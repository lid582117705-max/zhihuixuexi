package com.boxue.module.learn.feign;

import com.boxue.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * 调用订单服务（lb://boxue-order-service）的声明式客户端（OpenFeign）。
 * AI 工具查"我的已购课程"用：
 * 把当前用户 id 放在 X-User-Id 头传给订单服务，订单服务拦截器读进 UserContext 再查库。
 */
@FeignClient(name = "boxue-order-service")
public interface OrderClient {

    /** 对应 order-service 内部接口 GET /api/orders/mine/course-ids */
    @GetMapping("/api/orders/mine/course-ids")
    Result<List<Long>> myCourseIds(@RequestHeader("X-User-Id") Long userId);
}