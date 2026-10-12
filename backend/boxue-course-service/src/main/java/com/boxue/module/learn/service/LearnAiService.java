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
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 学习助教：基于 Spring AI ChatClient + 阿里云百炼 DashScope(OpenAI 兼容模式)。
 * - 多轮记忆：Redis 历史转 Message 一起发给模型
 * - 工具调用：CourseTools 的 @Tool 自动注册，框架内部完成调用循环
 * - 7-4 RAG：提问前先向量检索平台课程资料，把相关片段拼进提示词，让 AI 基于真实资料回答
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearnAiService {

    private final ChatClient.Builder chatClientBuilder;
    private final LearnChatService learnChatService;
    private final CourseTools courseTools;
    private final VectorStore vectorStore;

    /** 系统提示词：设定 AI 助教的人设；并引导它用工具查真实课程 */
    private static final String SYSTEM_PROMPT = "你是一名 Java 学习助教，请用中文、简洁、口语化地回答学生的问题；涉及平台课程信息时，请调用 query_courses 工具查询真实数据后再回答";
    /** RAG 每次检索取最相关的资料条数 */
    private static final int RAG_TOP_K = 3;

    /**
     * 带上下文 + 工具调用 + RAG 检索的 AI 对话
     */
    public String chat(String message) {
        // 0. 登录校验：从 ThreadLocal 拿当前用户 ID（网关塞的 X-User-Id 头）
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AuthException("请先登录");
        }

        // 1. 取出历史对话（user/assistant，从旧到新，最多 50 条）
        List<ChatMessage> history = learnChatService.list();

        // 2. 把历史转成 Spring AI 的 Message 对象
        List<Message> historyMessages = new ArrayList<>();
        for (ChatMessage m : history) {
            if ("user".equals(m.getRole())) {
                historyMessages.add(new UserMessage(m.getContent()));
            } else if ("assistant".equals(m.getRole())) {
                historyMessages.add(new AssistantMessage(m.getContent()));
            }
        }

        // 3. RAG：把用户问题向量化，去向量库找最相关的 3 段课程资料
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(message).topK(RAG_TOP_K).build());
        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
        // 检索到资料就把它们拼进系统提示词，作为回答依据；没检索到就用原提示词
        String systemPrompt = context.isBlank()
                ? SYSTEM_PROMPT
                : SYSTEM_PROMPT + "\n\n【平台资料】(回答时优先参考以下资料，资料里没有的内容要明说不知道):\n" + context;

        // 4. 一段链式调用：提示词(含RAG资料) + 历史 + 本次问题 + 工具 -> 调用模型 -> 取回答
        String reply = chatClientBuilder.build()
                .prompt()
                .system(systemPrompt)
                .messages(historyMessages.toArray(new Message[0]))
                .user(message)
                .tools(courseTools)
                .call()
                .content();

        if (reply == null || reply.isBlank()) {
            throw new BizException("AI 没有生成内容");
        }

        // 5. 把这一问一答存进 Redis
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