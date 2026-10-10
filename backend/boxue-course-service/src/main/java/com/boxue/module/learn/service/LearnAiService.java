package com.boxue.module.learn.service;

import com.boxue.common.auth.UserContext;
import com.boxue.common.exception.AuthException;
import com.boxue.common.exception.BizException;
import com.boxue.module.course.entity.Course;
import com.boxue.module.course.service.CourseService;
import com.boxue.module.learn.dto.ChatMessage;
import com.boxue.module.learn.dto.CreateChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 学习助教：直接调用本机 Ollama 的 /api/chat 接口
 * 7-2 多轮记忆：Redis 历史回放 + 一问一答落库
 * 7-3 工具调用：模型声明要调 query_courses -> 后端查真实课程 -> 结果回传 -> 模型组织回答
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearnAiService {

    private final RestClient.Builder restClientBuilder;
    private final LearnChatService learnChatService;
    private final CourseService courseService;
    private final ObjectMapper objectMapper;

    /** Ollama 服务地址，来自 application.yml 的 boxue.ai.ollama.base-url */
    @Value("${boxue.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    /** 使用的本地模型名，来自 boxue.ai.ollama.chat.options.model */
    @Value("${boxue.ai.ollama.chat.options.model}")
    private String model;

    /** 系统提示词：设定 AI 助教的人设；并引导它用工具查真实课程 */
    private static final String SYSTEM_PROMPT = "你是一名 Java 学习助教，请用中文、简洁、口语化地回答学生的问题；涉及平台课程信息时，请调用 query_courses 工具查询真实数据后再回答";

    /** 工具调用最多来回几轮，防止模型一直要工具导致死循环 */
    private static final int MAX_TOOL_ROUNDS = 5;

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

        // 2. 初始 messages：系统提示词 + 历史 + 本次问题
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        for (ChatMessage m : history) {
            messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }
        messages.add(Map.of("role", "user", "content", message));

        // 3. 工具调用循环：模型可能先要工具 -> 执行工具 -> 把结果喂回去 -> 再要/给最终回答
        String reply = null;
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            Map<?, ?> response = callOllama(messages, List.of(courseSearchTool()));
            Map<?, ?> msg = (Map<?, ?>) response.get("message");
            if (msg == null) {
                throw new BizException("AI 返回格式异常");
            }

            List<?> toolCalls = (List<?>) msg.get("tool_calls");
            if (toolCalls == null || toolCalls.isEmpty()) {
                // 模型没有要工具 -> 这次返回的就是最终回答
                Object content = msg.get("content");
                if (content instanceof String c && !c.isBlank()) {
                    reply = c;
                }
                break;
            }

            // 4. 协议要求：把带 tool_calls 的 assistant 消息原样追加进对话（模型下一轮要能看到自己刚才要了什么）
            Map<String, Object> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            Object contentObj = msg.get("content");
            assistantMsg.put("content", contentObj == null ? "" : contentObj);
            assistantMsg.put("tool_calls", toolCalls);
            messages.add(assistantMsg);

            // 5. 逐个执行工具，把结果以 role=tool 追加进对话（一条工具调用对应一条结果）
            for (Object call : toolCalls) {
                Map<?, ?> function = (Map<?, ?>) ((Map<?, ?>) call).get("function");
                if (function == null) {
                    continue;
                }
                String name = (String) function.get("name");
                Object args = function.get("arguments");
                String result = executeTool(name, args);
                messages.add(Map.of("role", "tool", "content", result));
            }
            // 继续下一轮：把带着工具结果的完整对话重新发给模型
        }

        if (reply == null || reply.isBlank()) {
            throw new BizException("AI 没有生成内容");
        }

        // 6. 把这一问一答存进 Redis（复用 LearnChatService.add）
        saveMessage("user", message);
        saveMessage("assistant", reply);

        log.info("[AI] 用户 {} 与 AI 完成一轮对话", userId);
        return reply;
    }

    /** 发一次 /api/chat 请求，带 messages 和 tools */
    private Map<?, ?> callOllama(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "stream", false,
                "messages", messages,
                "tools", tools
        );
        try {
            Map<?, ?> response = restClientBuilder.build()
                    .post()
                    .uri(ollamaBaseUrl + "/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw new BizException("AI 返回为空");
            }
            return response;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI] 调用 Ollama 失败: {}", e.getMessage(), e);
            throw new BizException("AI 服务暂时不可用，请确认本机 Ollama 已启动");
        }
    }

    /** 根据工具名执行真实业务，返回给模型的 JSON 文本 */
    private String executeTool(String name, Object args) {
        log.info("[AI] 执行工具 {} 参数 {}", name, args);
        switch (name) {
            case "query_courses" -> {
                String keyword = "";
                if (args instanceof Map<?, ?> argMap && argMap.get("keyword") instanceof String k) {
                    keyword = k;
                }
                List<Course> courses = courseService.searchByTitle(keyword);
                return toJson(courses);
            }
            default -> throw new BizException("未知工具: " + name);
        }
    }

    /** 把工具执行结果转成 JSON 文本（模型只读文本） */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException("工具结果序列化失败");
        }
    }

    /** 工具声明：告诉模型"你能调用这个函数，参数长这样"（OpenAI 风格 JSON Schema） */
    private Map<String, Object> courseSearchTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "query_courses",
                        "description", "按课程名称关键词查询平台在线课程，返回课程 id/标题/讲师/价格/简介",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "keyword", Map.of("type", "string", "description", "课程名称关键词，例如 Java")
                                ),
                                "required", List.of("keyword")
                        )
                )
        );
    }

    /** 往 Redis 追加一条对话记录（user 或 assistant） */
    private void saveMessage(String role, String content) {
        CreateChatRequest req = new CreateChatRequest();
        req.setRole(role);
        req.setContent(content);
        learnChatService.add(req);
    }
}