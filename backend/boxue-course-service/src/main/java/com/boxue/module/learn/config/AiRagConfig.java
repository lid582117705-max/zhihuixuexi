package com.boxue.module.learn.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 向量库配置：
 * SimpleVectorStore = 内存版向量库（够本项目学原理用）。
 * 生产可换 Redis/PGVector/Milvus 等（Spring AI 都有对应 starter）。
 */
@Configuration
public class AiRagConfig {

    /**
     * 向量库：负责"存入文档(自动转向量)"和"按相似度检索"。
     * EmbeddingModel 由 spring-ai-starter-model-openai 自动配置（走 DashScope 兼容端点的 /embeddings）。
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}