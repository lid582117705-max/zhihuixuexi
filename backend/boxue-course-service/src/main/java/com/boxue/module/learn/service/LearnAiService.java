package com.boxue.module.learn.service;

import com.boxue.common.auth.UserContext;
import com.boxue.common.exception.AuthException;
import com.boxue.common.exception.BizException;
import com.boxue.module.learn.dto.ChatMessage;
import com.boxue.module.learn.dto.CreateChatRequest;
import com.boxue.module.learn.tool.CourseTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 学习助教：基于 Spring AI ChatClient + 阿里云百炼 DashScope 通义千问（OpenAI 兼容模式）。
 * - 多轮记忆：Redis 里的历史对话转成 Spring AI Message 一起发给模型
 * - 工具调用：CourseTools 里的 @Tool 方法自动注册成 tools，ChatClient 内部自动完成调用循环
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearnAiService {

    private final ChatClient.Builder chatClientBuilder;
    private final LearnChatService learnChatService;
    private final CourseTools courseTools;

    /** 系统提示词：设定 AI 助教的人设；并引导它用工具查真实课程 */
    private static final String SYSTEM_PROMPT = "你是一名 Java 学习助教，请用中文、简洁、口语化地回答学生的问题；涉及平台课程信息时，请调用 query_courses 工具查询真实数据后再回答";

    /**
     * 带上下文 + 工具调用的 AI 对话
     */
    public String chat(String message) {
        // 0. 登录校验：从 ThreadLocal 拿当前用户 ID（网关塞的 X-User-Id 头）
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AuthException("请先登录");
        }

        // 1. 取出历史对话（user/assistant，从旧到新，最多 50 条）
        List<ChatMessage> history = learnChatService.list();

        // 2. 把历史转成 Spring AI 的 Message 对象（UserMessage = 用户说的，AssistantMessage = AI 说过的）
        List<Message> historyMessages = new ArrayList<>();
        for (ChatMessage m : history) {
            if ("user".equals(m.getRole())) {
                historyMessages.add(new UserMessage(m.getContent()));
            } else if ("assistant".equals(m.getRole())) {
                historyMessages.add(new AssistantMessage(m.getContent()));
            }
        }

        // 3. 一段链式调用搞定：系统提示词 + 历史回放 + 本次问题 + 工具注册 -> 调用模型 -> 取回答
        //    Spring AI 内部自动处理：工具声明转 JSON、模型要工具时执行再回传、必要时循环多轮
        String reply = chatClientBuilder.build()
                .prompt()
                .system(SYSTEM_PROMPT)
                .messages(historyMessages.toArray(new Message[0]))
                .user(message)
                .tools(courseTools)
                .call()
                .content();

        if (reply == null || reply.isBlank()) {
            throw new BizException("AI 没有生成内容");
        }

        // 4. 把这一问一答存进 Redis（复用 LearnChatService.add，自动带登录校验和 50 条截断）
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