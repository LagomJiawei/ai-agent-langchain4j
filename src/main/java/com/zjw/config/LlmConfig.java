package com.zjw.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LLM 核心配置 - LangChain4j
 *
 * @author ZhangJw
 */
@Configuration
public class LlmConfig {

    @Resource
    private LlmProperties llmProperties;

    /**
     * 配置 ChatLanguageModel (通义千问兼容 OpenAI 接口)
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(llmProperties.getApiKey())
                .baseUrl(llmProperties.getBaseUrl())
                .modelName(llmProperties.getModelName())
                .temperature(0.7)
                .timeout(llmProperties.getTimeout())
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    /**
     * 配置 StreamingChatLanguageModel (流式响应)
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(llmProperties.getApiKey())
                .baseUrl(llmProperties.getBaseUrl())
                .modelName(llmProperties.getModelName())
                .temperature(0.7)
                .timeout(llmProperties.getTimeout())
                .build();
    }

    /**
     * 配置 EmbeddingModel (用于 RAG 向量检索)
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(llmProperties.getApiKey())
                .baseUrl(llmProperties.getBaseUrl())
                .modelName("text-embedding-v3")
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 配置默认 EmbeddingStore (内存存储向量数据库)
     * Milvus 启用时由 MilvusConfig 提供
     */
    @Bean
    @ConditionalOnMissingBean
    public EmbeddingStore<TextSegment> inMemoryEmbeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
}
