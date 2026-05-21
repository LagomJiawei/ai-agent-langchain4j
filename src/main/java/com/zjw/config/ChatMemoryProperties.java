package com.zjw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author ZhangJw
 */
@Data
@Component
@ConfigurationProperties(prefix = "chat-memory")
public class ChatMemoryProperties {
    private String baseDir = System.getProperty("user.dir") + "/chat-memory";
    private int maxMessages = 20;
    private String storeType = "file";
}