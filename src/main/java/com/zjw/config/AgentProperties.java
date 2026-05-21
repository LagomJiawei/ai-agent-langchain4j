package com.zjw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author ZhangJw
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    private String mode = "AUTO";
    private int maxIterations = 10;
    private boolean enableLoopDetection = true;
    private int loopThreshold = 2;
}