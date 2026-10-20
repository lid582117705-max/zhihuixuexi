package com.boxue.module.order.controller;

import com.boxue.common.result.Result;
import com.boxue.module.order.entity.DeadLetter;
import com.boxue.module.order.service.DeadLetterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 死信管理接口：查询 / 重放 / 忽略
 *
 * 说明：项目还没有 RBAC，这个接口目前只要登录就能访问。
 * 生产环境应该放到管理端并加管理员权限校验。
 */
@Tag(name = "订单服务-死信管理", description = "死信查询 / 重放 / 忽略")
@RestController
@RequestMapping("/api/dead-letters")
@RequiredArgsConstructor
public class DeadLetterController {

    private final DeadLetterService deadLetterService;

    @Operation(summary = "死信列表", description = "默认最近 100 条，status：0待处理 1已重放 2已忽略")
    @GetMapping
    public Result<List<DeadLetter>> list(@RequestParam(required = false) Integer status) {
        return Result.ok(deadLetterService.list(status));
    }

    @Operation(summary = "重放死信", description = "清幂等标记后投回原队列，并把记录标记为已重放")
    @PostMapping("/{id}/replay")
    public Result<Void> replay(@PathVariable Long id) {
        deadLetterService.replay(id);
        return Result.ok();
    }

    @Operation(summary = "忽略死信", description = "脏数据等无法重放的记录，人工确认后归档")
    @PostMapping("/{id}/ignore")
    public Result<Void> ignore(@PathVariable Long id) {
        deadLetterService.ignore(id);
        return Result.ok();
    }
}
