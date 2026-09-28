package com.boxue.module.learn.controller;

import com.boxue.common.result.Result;
import com.boxue.module.learn.dto.ChatMessage;
import com.boxue.module.learn.dto.CreateChatRequest;
import com.boxue.module.learn.service.LearnChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/learn/chat")
@RequiredArgsConstructor
public class LearnChatController {

    private final LearnChatService learnChatService;

    @PostMapping
    public Result<ChatMessage> add(@Valid @RequestBody CreateChatRequest request) {
        return Result.ok(learnChatService.add(request));
    }

    @GetMapping
    public Result<List<ChatMessage>> list() {
        return Result.ok(learnChatService.list());
    }
}