package com.zjw.chatmemory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 聊天记忆配置
 * 支持多种存储后端:
 * - 内存: InMemoryChatMemoryStore
 * - 文件: FileChatMemoryStore
 * - Redis: RedisChatMemoryStore (生产环境推荐，支持集群共享)
 *
 * @author ZhangJw
 */
@Slf4j
@Configuration
public class ChatMemoryConfig {

    @Value("${chat-memory.base-dir:${user.dir}/chat-memory}")
    private String baseDir;

    @Value("${chat-memory.max-messages:20}")
    private int maxMessages;

    @Value("${chat-memory.store-type:file}")
    private String storeType;

    @Autowired(required = false)
    private RedisChatMemoryStore redisChatMemoryStore;

    /**
     * Redis 分布式存储（优先级最高）
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "redis.enabled", havingValue = "true")
    public ChatMemoryStore redisChatMemoryStorePrimary() {
        log.info("使用 Redis 分布式聊天记忆存储");
        return redisChatMemoryStore;
    }

    /**
     * 文件持久化 ChatMemory
     */
    @Bean
    @ConditionalOnProperty(name = "chat-memory.store-type", havingValue = "file", matchIfMissing = true)
    public ChatMemoryStore fileChatMemoryStore() {
        log.info("使用文件持久化聊天记忆存储");
        return new FileChatMemoryStore(baseDir);
    }

    /**
     * 纯内存 ChatMemory（不持久化）
     */
    @Bean
    @ConditionalOnProperty(name = "chat-memory.store-type", havingValue = "memory")
    public ChatMemoryStore inMemoryChatMemoryStore() {
        log.info("使用纯内存聊天记忆存储（不持久化）");
        return new InMemoryChatMemoryStore();
    }

    @Autowired
    private ChatMemoryStore chatMemoryStore;

    /**
     * 创建带消息窗口的 ChatMemory
     */
    public ChatMemory createChatMemory(String conversationId) {
        return MessageWindowChatMemory.builder()
                .id(conversationId)
                .maxMessages(maxMessages)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }

    /**
     * 创建临时 ChatMemory (仅内存，不持久化)
     */
    public ChatMemory createTemporaryChatMemory(String conversationId) {
        return MessageWindowChatMemory.builder()
                .id(conversationId)
                .maxMessages(maxMessages)
                .build();
    }
}
