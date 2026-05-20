package com.zjw.controller;

import com.zjw.agent.AgentState;
import com.zjw.app.FinancialAdvisorService;
import com.zjw.common.ResultUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 理财顾问 API
 *
 * @author ZhangJw
 */
@Slf4j
@RestController
@RequestMapping("/api/financial")
public class FinancialController {

    @Resource
    private FinancialAdvisorService financialAdvisorService;

    /**
     * 普通对话
     */
    @GetMapping("/chat")
    public ResultUtils<String> chat(String message, String chatId) {
        log.info("普通对话请求: chatId={}, message={}", chatId, message);
        String result = financialAdvisorService.chat(message, chatId);
        return ResultUtils.success(result);
    }

    /**
     * 使用 RAG 知识库对话
     */
    @GetMapping("/chat/rag")
    public ResultUtils<String> chatWithRag(String message, String chatId) {
        log.info("RAG 对话请求: chatId={}, message={}", chatId, message);
        String result = financialAdvisorService.chatWithRag(message, chatId);
        return ResultUtils.success(result);
    }

    /**
     * 使用 ReAct Agent 处理复杂任务
     */
    @GetMapping("/chat/agent")
    public ResultUtils<AgentState> chatWithAgent(String message) {
        log.info("Agent 任务请求: message={}", message);
        AgentState result = financialAdvisorService.chatWithAgent(message);
        return ResultUtils.success(result);
    }

    /**
     * 清空对话记忆
     */
    @PostMapping("/memory/clear")
    public ResultUtils<Void> clearChatMemory(String chatId) {
        log.info("清空对话记忆: chatId={}", chatId);
        financialAdvisorService.clearChatMemory(chatId);
        return ResultUtils.success();
    }
}
