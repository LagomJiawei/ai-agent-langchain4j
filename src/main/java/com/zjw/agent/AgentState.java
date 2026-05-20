package com.zjw.agent;

import dev.langchain4j.data.message.ChatMessage;
import lombok.Builder;
import lombok.Data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 状态 - LangGraph4j 状态管理
 *
 * @author ZhangJw
 */
@Data
@Builder
public class AgentState {

    /**
     * 用户原始问题
     */
    private String userQuery;

    /**
     * 当前思考内容
     */
    private String currentThought;

    /**
     * 消息历史
     */
    @Builder.Default
    private List<ChatMessage> messageHistory = new ArrayList<>();

    /**
     * 已调用的工具记录
     */
    @Builder.Default
    private List<ToolCallRecord> toolCallHistory = new ArrayList<>();

    /**
     * 当前步骤
     */
    @Builder.Default
    private int currentStep = 0;

    /**
     * 最大步骤数
     */
    @Builder.Default
    private int maxSteps = 10;

    /**
     * 循环检测计数 - 精确匹配
     */
    @Builder.Default
    private Map<String, Integer> loopDetectionMap = new ConcurrentHashMap<>();

    /**
     * 工具调用计数
     */
    @Builder.Default
    private Map<String, Integer> toolUsageCount = new ConcurrentHashMap<>();

    /**
     * 是否完成
     */
    @Builder.Default
    private boolean isFinished = false;

    /**
     * 最终答案
     */
    private String finalAnswer;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 工具调用记录
     */
    @Data
    @Builder
    public static class ToolCallRecord {
        private String toolName;
        private String arguments;
        private String result;
        private long timestamp;
    }

    /**
     * 增加步骤计数
     */
    public void incrementStep() {
        this.currentStep++;
    }

    /**
     * 检查是否达到最大步骤数
     */
    public boolean isMaxStepsReached() {
        return currentStep >= maxSteps;
    }

    /**
     * 记录工具调用，用于循环检测
     */
    public void recordToolCall(String toolName, String arguments) {
        // 精确匹配计数
        String key = toolName + ":" + arguments;
        loopDetectionMap.merge(key, 1, Integer::sum);

        // 工具使用计数
        toolUsageCount.merge(toolName, 1, Integer::sum);
    }

    /**
     * 检测是否存在循环调用（精确匹配）
     */
    public boolean isInLoop(String toolName, String arguments) {
        String key = toolName + ":" + arguments;
        return loopDetectionMap.getOrDefault(key, 0) >= 2;
    }

    /**
     * 检测工具是否过度使用
     */
    public boolean isToolOverused(String toolName) {
        return toolUsageCount.getOrDefault(toolName, 0) >= 3;
    }

    /**
     * 获取循环检测建议
     */
    public String getLoopSuggestion(String toolName) {
        List<String> suggestions = new ArrayList<>();

        if (isToolOverused(toolName)) {
            suggestions.add("工具 " + toolName + " 已调用" + toolUsageCount.get(toolName) + "次，建议换用其他工具");
        }

        if ("searchWeb".equals(toolName)) {
            suggestions.add("建议尝试修改搜索关键词，使用更精确的表述");
        } else if ("scrapeWebPage".equals(toolName)) {
            suggestions.add("建议换用其他链接或通过搜索工具获取摘要信息");
        } else if ("readFile".equals(toolName)) {
            suggestions.add("建议检查文件路径是否正确，或尝试读取其他相关文件");
        }

        suggestions.add("如果多次尝试无效，请基于已有信息直接给出答案");

        return String.join("。", suggestions);
    }

    /**
     * 获取最近的工具调用记录
     */
    public List<ToolCallRecord> getRecentToolCalls(int limit) {
        if (toolCallHistory.isEmpty()) {
            return Collections.emptyList();
        }
        int start = Math.max(0, toolCallHistory.size() - limit);
        return new ArrayList<>(toolCallHistory.subList(start, toolCallHistory.size()));
    }
}
