package com.boxue.module.seckill.controller;

import com.boxue.common.result.Result;
import com.boxue.module.seckill.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "订单服务-秒杀", description = "Redis Lua 预扣库存 + MQ 削峰")
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /** 秒杀：Redis Lua 预扣库存 + MQ 削峰落库（需要登录） */
    @Operation(summary = "秒杀下单", description = "Lua 原子预扣库存，成功发 MQ 异步落库")
    @PostMapping("/{courseId}")
    public Result<Map<String, Object>> seckill(@PathVariable Long courseId) {
        return Result.ok(seckillService.seckill(courseId));
    }
}