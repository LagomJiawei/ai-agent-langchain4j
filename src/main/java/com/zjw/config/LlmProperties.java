package com.zjw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * LLM 配置属性
 *
 * @author ZhangJw
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm.dashscope")
public class LlmProperties {

    private String apiKey;
    private String baseUrl;
    private String modelName = "qwen-max";
    private Duration timeout = Duration.ofSeconds(60);
}
