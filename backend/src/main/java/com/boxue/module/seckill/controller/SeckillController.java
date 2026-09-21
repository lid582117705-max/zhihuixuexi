package com.boxue.module.seckill.controller;

import com.boxue.common.result.Result;
import com.boxue.module.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /** 秒杀：Redis Lua 预扣库存 + MQ 削峰落库（需要登录） */
    @PostMapping("/{courseId}")
    public Result<Map<String, Object>> seckill(@PathVariable Long courseId) {
        return Result.ok(seckillService.seckill(courseId));
    }
}