package com.boxue.module.learn.service;

import com.boxue.common.auth.UserContext;
import com.boxue.common.exception.AuthException;
import com.boxue.common.exception.BizException;
import com.boxue.module.learn.dto.ChatMessage;
import com.boxue.module.learn.dto.CreateChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 学习助教：直接调用本机 Ollama 的 /api/chat 接口
 * 7-2 多轮记忆：每次提问把 Redis 里的历史对话一起发给模型，并把这一问一答存回 Redis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearnAiService {

    private final RestClient.Builder restClientBuilder;
    private final LearnChatService learnChatService;

    /** Ollama 服务地址，来自 application.yml 的 boxue.ai.ollama.base-url */
    @Value("${boxue.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    /** 使用的本地模型名，来自 boxue.ai.ollama.chat.options.model */
    @Value("${boxue.ai.ollama.chat.options.model}")
    private String model;

    /** 系统提示词：设定 AI 助教的人设（固定在最前面） */
    private static final String SYSTEM_PROMPT = "你是一名 Java 学习助教，请用中文、简洁、口语化地回答学生的问题";

    /**
     * 带上下文的 AI 对话：历史 + 本次问题 -> 模型回答 -> 存入 Redis
     */
    public String chat(String message) {
        // 0. 登录校验：从 ThreadLocal 拿当前用户 ID（网关塞的 X-User-Id 头）
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AuthException("请先登录");
        }

        // 1. 取出这个用户的历史对话（user/assistant，从旧到新，最多 50 条）
        List<ChatMessage> history = learnChatService.list();

        // 2. 组装 messages：系统提示词 + 历史对话 + 本次问题
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        for (ChatMessage m : history) {
            messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }
        messages.add(Map.of("role", "user", "content", message));

        // 3. 组装 Ollama /api/chat 需要的请求体（messages 用了可变的 ArrayList）
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "stream", false,
                "messages", messages
        );

        // 4. 发 HTTP POST 请求，等待 Ollama 返回
        Map<?, ?> response;
        try {
            response = restClientBuilder.build()
                    .post()
                    .uri(ollamaBaseUrl + "/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("[AI] 调用 Ollama 失败: {}", e.getMessage(), e);
            throw new BizException("AI 服务暂时不可用，请确认本机 Ollama 已启动");
        }

        // 5. 从返回体里取出 message.content，就是模型的回答
        if (response == null) {
            throw new BizException("AI 返回为空");
        }
        Object messageObj = response.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            throw new BizException("AI 返回格式异常");
        }
        Object content = messageMap.get("content");
        if (!(content instanceof String reply) || reply.isBlank()) {
            throw new BizException("AI 没有生成内容");
        }

        // 6. 把这一问一答存进 Redis（复用 LearnChatService.add，自动带登录校验和 50 条截断）
        saveMessage("user", message);
        saveMessage("assistant", reply);

        log.info("[AI] 用户 {} 与 AI 完成一轮对话", userId);
        return reply;
    }

    /** 往 Redis 追加一条对话记录（user 或 assistant） */
    private void saveMessage(String role, String content) {
        CreateChatRequest req = new CreateChatRequest();
        req.setRole(role);
        req.setContent(content);
        learnChatService.add(req);
    }
}