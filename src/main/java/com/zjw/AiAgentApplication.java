package com.zjw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Agent 应用启动类
 * 基于 LangChain4j + LangGraph4j 重构
 *
 * @author ZhangJw
 */
@SpringBootApplication
public class AiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentApplication.class, args);
    }
}
