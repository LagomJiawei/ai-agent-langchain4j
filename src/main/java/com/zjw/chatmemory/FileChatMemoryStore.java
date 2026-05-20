package com.zjw.chatmemory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于文件的 ChatMemory 持久化存储
 * 使用 JSON 序列化，跨语言兼容
 *
 * @author ZhangJw
 */
@Slf4j
public class FileChatMemoryStore implements ChatMemoryStore {

    private final String baseDir;
    private final ObjectMapper objectMapper;
    private final Map<Object, List<ChatMessage>> cache = new ConcurrentHashMap<>();

    public FileChatMemoryStore(String baseDir) {
        this.baseDir = baseDir;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();

        File dir = new File(baseDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try {
            Path filePath = getFilePath(memoryId);
            if (Files.exists(filePath)) {
                String json = Files.readString(filePath);
                List<ChatMessage> messages = objectMapper.readValue(json, new TypeReference<>() {});
                cache.put(memoryId, messages);
                return messages;
            }
        } catch (IOException e) {
            log.error("加载对话失败: {}", memoryId, e);
        }
        return new ArrayList<>();
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            cache.put(memoryId, messages);
            Path filePath = getFilePath(memoryId);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(messages);
            Files.writeString(filePath, json);
        } catch (IOException e) {
            log.error("保存对话失败: {}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            cache.remove(memoryId);
            Path filePath = getFilePath(memoryId);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("删除对话失败: {}", memoryId, e);
        }
    }

    private Path getFilePath(Object memoryId) {
        return Paths.get(baseDir, "chat_" + memoryId + ".json");
    }

    /**
     * 清空缓存 (内存回收时使用)
     */
    public void clearCache() {
        cache.clear();
    }
}
