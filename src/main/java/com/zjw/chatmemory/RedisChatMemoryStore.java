package com.zjw.chatmemory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式聊天记忆存储
 * 支持集群环境下多实例共享对话历史
 *
 * @author ZhangJw
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "redis.enabled", havingValue = "true")
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String MEMORY_KEY_PREFIX = "chat:memory:";
    private static final long TTL_DAYS = 7;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = buildKey(memoryId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(json, new TypeReference<List<ChatMessage>>() {});
        } catch (JsonProcessingException e) {
            log.error("反序列化聊天记忆失败: {}", memoryId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = buildKey(memoryId);
        try {
            String json = objectMapper.writeValueAsString(messages);
            redisTemplate.opsForValue().set(key, json, TTL_DAYS, TimeUnit.DAYS);
            log.debug("更新聊天记忆: {}, 消息数: {}", memoryId, messages.size());
        } catch (JsonProcessingException e) {
            log.error("序列化聊天记忆失败: {}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = buildKey(memoryId);
        Boolean deleted = redisTemplate.delete(key);
        log.info("删除聊天记忆: {}, 结果: {}", memoryId, deleted);
    }

    private String buildKey(Object memoryId) {
        return MEMORY_KEY_PREFIX + memoryId;
    }
}
