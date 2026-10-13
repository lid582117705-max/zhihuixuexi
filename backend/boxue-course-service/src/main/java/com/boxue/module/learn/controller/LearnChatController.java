package com.boxue.module.learn.controller;

import com.boxue.common.result.Result;
import com.boxue.module.learn.dto.ChatMessage;
import com.boxue.module.learn.dto.CreateChatRequest;
import com.boxue.module.learn.service.LearnChatService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "课程服务-学习对话", description = "Redis List 多轮对话记录")
@RestController
@RequestMapping("/api/learn/chat")
@RequiredArgsConstructor
public class LearnChatController {

    private final LearnChatService learnChatService;

    @Operation(summary = "新增一条对话记录", description = "当前用户，写入 Redis List")
    @PostMapping
    public Result<ChatMessage> add(@Valid @RequestBody CreateChatRequest request) {
        return Result.ok(learnChatService.add(request));
    }

    @Operation(summary = "查询对话历史", description = "当前用户最多 50 条")
    @GetMapping
    public Result<List<ChatMessage>> list() {
        return Result.ok(learnChatService.list());
    }
}