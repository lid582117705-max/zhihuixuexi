package com.boxue.module.learn.service;

import com.boxue.common.auth.UserContext;
import com.boxue.common.exception.AuthException;
import com.boxue.common.exception.BizException;
import com.boxue.module.learn.dto.ChatMessage;
import com.boxue.module.learn.dto.CreateChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearnChatService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** Redis key：boxue:learn:chat:{userId}，每个用户一条 List */
    private static final String CHAT_KEY_PREFIX = "boxue:learn:chat:";
    /** 最多保留最近 50 条，防止无限增长 */
    private static final long MAX_MESSAGES = 50;

    /** 追加一条对话消息 */
    public ChatMessage add(CreateChatRequest request) {
        Long userId = requireLoginUserId();

        String role = request.getRole();
        if (!"user".equals(role) && !"assistant".equals(role)) {
            throw new BizException("角色只能是 user 或 assistant");
        }

        ChatMessage message = new ChatMessage(role, request.getContent(), LocalDateTime.now());
        String key = CHAT_KEY_PREFIX + userId;
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.warn("[对话] 序列化失败", e);
            throw new BizException("消息序列化失败");
        }

        // rightPush：往 List 尾部追加（最新一条在最后）
        redisTemplate.opsForList().rightPush(key, json);
        // trim：只保留最近 50 条，丢弃更早的
        redisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);

        log.info("[对话] 用户 {} 新增一条 {} 消息", userId, role);
        return message;
    }

    /** 取出当前用户全部对话（按时间从旧到新） */
    public List<ChatMessage> list() {
        Long userId = requireLoginUserId();
        String key = CHAT_KEY_PREFIX + userId;

        // range(key, 0, -1)：从第 0 条到最后一条，全部取出
        List<String> jsons = redisTemplate.opsForList().range(key, 0, -1);
        if (jsons == null) {
            return new ArrayList<>();
        }

        List<ChatMessage> messages = new ArrayList<>();
        for (String json : jsons) {
            try {
                messages.add(objectMapper.readValue(json, ChatMessage.class));
            } catch (Exception e) {
                log.warn("[对话] 反序列化失败，跳过一条：{}", json);
            }
        }
        return messages;
    }

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AuthException("请先登录");
        }
        return userId;
    }
}