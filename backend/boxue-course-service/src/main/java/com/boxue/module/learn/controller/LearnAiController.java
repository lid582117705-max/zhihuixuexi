package com.boxue.module.learn.controller;

import com.boxue.common.result.Result;
import com.boxue.module.learn.dto.ChatRequest;
import com.boxue.module.learn.service.LearnAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 学习助教接口
 */
@RestController
@RequestMapping("/api/learn/ai")
@RequiredArgsConstructor
public class LearnAiController {

    private final LearnAiService learnAiService;

    /** 问 AI 一句话，返回 AI 的回答 */
    @PostMapping("/chat")
    public Result<String> chat(@Valid @RequestBody ChatRequest request) {
        return Result.ok(learnAiService.chat(request.getMessage()));
    }
}