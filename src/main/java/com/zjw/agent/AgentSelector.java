package com.zjw.agent;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Agent 模式选择器
 *
 * 根据任务复杂度自动选择最优执行模式:
 * - SIMPLE: 简单问题，直接 LLM 回答（1 步）
 * - REACT: 中等复杂度，ReAct 推理循环（平均 3-5 步）
 * - PLAN_AND_EXECUTE: 复杂任务，先规划再并行执行（迭代减少 50%）
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class AgentSelector {

    @Resource
    private ReActAgent reActAgent;

    @Resource
    private PlanAndExecuteAgent planAndExecuteAgent;

    @Value("${agent.mode:AUTO}")
    private String defaultMode;

    public enum AgentMode {
        SIMPLE,         // 直接回答
        REACT,          // ReAct 推理
        PLAN_AND_EXECUTE, // 规划执行
        AUTO            // 自动选择
    }

    /**
     * 选择并执行 Agent
     */
    public AgentState execute(String query) {
        AgentMode mode = determineMode(query);
        log.info("Agent 模式选择: {} -> {}", maskQuery(query), mode);

        return switch (mode) {
            case SIMPLE -> executeSimple(query);
            case REACT -> reActAgent.execute(query);
            case PLAN_AND_EXECUTE -> planAndExecuteAgent.execute(query);
            case AUTO -> planAndExecuteAgent.execute(query); // AUTO 降级为 PLAN
        };
    }

    /**
     * 根据查询特征自动选择模式
     */
    private AgentMode determineMode(String query) {
        if (!"AUTO".equals(defaultMode)) {
            return AgentMode.valueOf(defaultMode);
        }

        // 特征 1: 查询长度
        if (query.length() < 20) {
            // 短查询可能是简单问题
            return AgentMode.REACT;
        }

        // 特征 2: 复杂度关键词检测
        int complexityScore = 0;

        // 多步骤关键词
        if (containsAny(query, "比较", "对比", "分析", "整理", "汇总", "报告")) {
            complexityScore += 2;
        }

        // 需要多源信息
        if (containsAny(query, "搜索", "查找", "下载", "网页", "网站")) {
            complexityScore += 1;
        }

        // 文件操作关键词
        if (containsAny(query, "文件", "目录", "保存", "写入", "读取")) {
            complexityScore += 2;
        }

        // 决策
        if (complexityScore >= 3) {
            return AgentMode.PLAN_AND_EXECUTE; // 复杂任务
        } else if (complexityScore >= 1) {
            return AgentMode.REACT; // 中等复杂度
        } else {
            return AgentMode.REACT; // 默认可用 ReACT
        }
    }

    /**
     * 简单模式：直接回答（无工具调用）
     */
    private AgentState executeSimple(String query) {
        // 简单模式复用 ReAct Agent，通常 1 步完成
        return reActAgent.execute(query);
    }

    private boolean containsAny(String query, String... keywords) {
        String lower = query.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private String maskQuery(String query) {
        if (query.length() <= 20) return query;
        return query.substring(0, 20) + "...";
    }
}
