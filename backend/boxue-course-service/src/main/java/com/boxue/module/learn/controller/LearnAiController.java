package com.boxue.module.learn.controller;

import com.boxue.common.result.Result;
import com.boxue.module.learn.dto.ChatRequest;
import com.boxue.module.learn.service.LearnAiService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 学习助教接口
 */
@Tag(name = "课程服务-AI 助教", description = "多轮记忆 + 工具调用 + RAG 检索")
@RestController
@RequestMapping("/api/learn/ai")
@RequiredArgsConstructor
public class LearnAiController {

    private final LearnAiService learnAiService;

    /** 问 AI 一句话，返回 AI 的回答 */
    @Operation(summary = "AI 对话", description = "回放 Redis 历史 + RAG 检索 + 工具调用，回答落库")
    @PostMapping("/chat")
    public Result<String> chat(@Valid @RequestBody ChatRequest request) {
        return Result.ok(learnAiService.chat(request.getMessage()));
    }
}