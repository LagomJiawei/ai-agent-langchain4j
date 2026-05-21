package com.zjw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author ZhangJw
 */
@Data
@Component
@ConfigurationProperties(prefix = "tool")
public class ToolProperties {

    private RateLimit rateLimit = new RateLimit();
    private Limit limit = new Limit();
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    @Data
    public static class RateLimit {
        private boolean enabled = true;
    }

    @Data
    public static class Limit {
        private int concurrency = 5;
        private int qps = 10;
    }

    @Data
    public static class CircuitBreaker {
        private int failThreshold = 5;
        private int recoverySeconds = 30;
    }
}