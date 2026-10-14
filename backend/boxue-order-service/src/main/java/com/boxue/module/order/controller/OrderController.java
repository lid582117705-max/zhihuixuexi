package com.boxue.module.order.controller;

import com.boxue.common.result.Result;
import com.boxue.module.order.dto.CreateOrderRequest;
import com.boxue.module.order.dto.MineCourseVO;
import java.util.List;
import com.boxue.module.order.entity.Order;
import com.boxue.module.order.service.OrderService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "订单服务-订单", description = "下单 / 支付 / 我的已购 / 延时关单")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单", description = "Redisson 防重 + RabbitMQ 延时关单（15 秒 TTL）")
    @PostMapping
    public Result<Order> create(@Valid @RequestBody CreateOrderRequest request) {
        return Result.ok(orderService.create(request));
    }

    /** 我的已购课程：已支付订单 + 课程信息组装 */
    @Operation(summary = "我的已购课程", description = "已支付订单 + 课程信息批量组装")
    @GetMapping("/mine")
    public Result<List<MineCourseVO>> mine() {
        return Result.ok(orderService.mine());
    }
    @Operation(summary = "订单详情", description = "只能查自己的订单")
    @GetMapping("/{id}")
    public Result<Order> detail(@PathVariable Long id) {
        return Result.ok(orderService.getById(id));
    }

    /** 内部接口（Feign 用）：返回当前用户已支付订单的课程 id 列表，AI 工具查"我的已购"走这里 */
    @Operation(summary = "我的已购课程 id（内部接口）", description = "AI 工具 list_my_courses 走这里")
    @GetMapping("/mine/course-ids")
    public Result<List<Long>> mineCourseIds() {
        return Result.ok(orderService.mineCourseIds());
    }

    /** 模拟支付成功 */
    @Operation(summary = "模拟支付成功", description = "订单状态 0→1，解锁防重")
    @PostMapping("/{id}/pay")
    public Result<Order> pay(@PathVariable Long id) {
        return Result.ok(orderService.pay(id));
    }
}