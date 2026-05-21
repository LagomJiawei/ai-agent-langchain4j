package com.zjw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author ZhangJw
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private String documentDir;
    private int topK = 5;
    private int recallK = 20;
    private double similarityThreshold = 0.7;
    private boolean smartSplit = true;
    private boolean rerankEnabled = true;
    private String rerankStrategy = "HYBRID_SCORE";
    private boolean cacheEnabled = true;
    private boolean parallelEnabled = true;

    private RedisCache redisCache = new RedisCache();
    private SemanticCache semanticCache = new SemanticCache();

    public RagProperties() {
        this.documentDir = System.getProperty("user.dir") + "/documents";
    }

    @Data
    public static class RedisCache {
        private boolean enabled = true;
    }

    @Data
    public static class SemanticCache {
        private boolean enabled = true;
        private double threshold = 0.9;
    }
}