package com.boxue.module.order.controller;

import com.boxue.common.result.Result;
import com.boxue.module.order.dto.CreateOrderRequest;
import com.boxue.module.order.dto.MineCourseVO;
import java.util.List;
import com.boxue.module.order.entity.Order;
import com.boxue.module.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Result<Order> create(@Valid @RequestBody CreateOrderRequest request) {
        return Result.ok(orderService.create(request));
    }

    /** 我的已购课程：已支付订单 + 课程信息组装 */
    @GetMapping("/mine")
    public Result<List<MineCourseVO>> mine() {
        return Result.ok(orderService.mine());
    }
    @GetMapping("/{id}")
    public Result<Order> detail(@PathVariable Long id) {
        return Result.ok(orderService.getById(id));
    }

    /** 模拟支付成功 */
    @PostMapping("/{id}/pay")
    public Result<Order> pay(@PathVariable Long id) {
        return Result.ok(orderService.pay(id));
    }
}