package com.zjw.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * ReAct Agent - 思考-行动循环 Agent
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class ReActAgent {

    @Resource
    private ChatLanguageModel chatLanguageModel;

    @Resource
    private List<ToolSpecification> toolSpecifications;

    @Resource
    private Map<String, ToolExecutor> toolExecutors;

    @Value("${agent.max-iterations:10}")
    private int maxIterations;

    private static final String SYSTEM_PROMPT = """
            你是 LiCaiManus，一个全能的 AI 助手，使用 ReAct 推理框架工作。

            你的工作流程：
            1. Thought: 思考当前问题，分析需要做什么
            2. Action: 选择合适的工具执行动作并调用
            3. Observation: 查看工具执行结果
            4. Repeat or Finish: 重复或给出最终答案

            【核心规则】
            - 可以多次调用工具来获取信息
            - 每次只调用一个工具，不要并行调用
            - 当获取足够信息后，使用 doTerminate 工具结束并给出答案
            - 用中文思考和回答

            【循环防御规则 - 必须严格遵守】
            1. 不要重复调用相同工具使用相同参数
            2. 如果某个工具连续2次没有返回有效信息，立即换其他工具或方法
            3. 如果搜索结果不理想，尝试修改关键词而不是重复相同搜索
            4. 如果网页抓取失败，尝试其他链接或使用搜索工具获取摘要
            5. 最多尝试2种不同方法，然后基于已有信息给出答案
            6. 绝对禁止陷入循环调用，效率优先于完美
            """;

    /**
     * 执行 Agent 任务
     */
    public AgentState execute(String userQuery) {
        // 初始化状态
        AgentState state = AgentState.builder()
                .userQuery(userQuery)
                .maxSteps(maxIterations)
                .build();

        // 添加系统消息和用户消息
        state.getMessageHistory().add(SystemMessage.from(SYSTEM_PROMPT));
        state.getMessageHistory().add(UserMessage.from(userQuery));

        log.info("Agent 开始执行任务: {}", userQuery);

        // ReAct 循环
        while (!state.isFinished() && !state.isMaxStepsReached()) {
            state.incrementStep();
            log.info("执行第 {} 步", state.getCurrentStep());

            // 1. 思考 (Reasoning
            AiMessage aiMessage = think(state);

            // 2. 检查是否有工具调用
            if (aiMessage.hasToolExecutionRequests()) {
                // 3. 行动 (Acting) - 有工具调用，执行工具
                act(state, aiMessage);
            } else {
                // 没有工具调用，任务完成
                state.setFinished(true);
                state.setFinalAnswer(aiMessage.text());
                log.info("Agent 任务完成");
            }
        }

        if (state.isMaxStepsReached() && !state.isFinished()) {
            state.setFinished(true);
            state.setFinalAnswer("达到最大迭代次数，任务终止。");
        }

        return state;
    }

    /**
     * 思考阶段 - 调用 LLM 决定下一步
     */
    private AiMessage think(AgentState state) {
        Response<AiMessage> response = chatLanguageModel.generate(
                state.getMessageHistory(),
                toolSpecifications
        );
        AiMessage aiMessage = response.content();
        state.getMessageHistory().add(aiMessage);
        state.setCurrentThought(aiMessage.text());

        if (aiMessage.hasToolExecutionRequests()) {
            List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
            log.info("决定调用工具: {}", requests.stream()
                    .map(r -> r.name())
                    .toList());
        } else {
            log.info("思考结果: {}", aiMessage.text());
        }

        return aiMessage;
    }

    /**
     * 行动阶段 - 执行工具调用
     */
    private void act(AgentState state, AiMessage aiMessage) {
        List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();

        for (ToolExecutionRequest request : toolRequests) {
            String toolName = request.name();
            String arguments = request.arguments();

            // 循环检测 - 精确匹配
            if (state.isInLoop(toolName, arguments)) {
                log.warn("检测到精确循环调用: {} 相同参数已调用2次", toolName);
                state.getMessageHistory().add(UserMessage.from(
                        "【循环防御】检测到相同工具调用重复2次。" + state.getLoopSuggestion(toolName)
                ));
                continue;
            }

            // 工具过度使用检测
            if (state.isToolOverused(toolName)) {
                log.warn("检测到工具过度使用: {} 已调用3次", toolName);
                state.getMessageHistory().add(UserMessage.from(
                        "【循环防御】工具 " + toolName + " 已调用3次。" + state.getLoopSuggestion(toolName)
                ));
                continue;
            }

            state.recordToolCall(toolName, arguments);

            // 执行工具
            ToolExecutor executor = toolExecutors.get(toolName);
            if (executor == null) {
                log.error("未知工具: {}", toolName);
                state.getMessageHistory().add(ToolExecutionResultMessage.from(
                        request,
                        "错误: 未知工具 " + toolName
                ));
                continue;
            }

            try {
                String result = executor.execute(request, state.getUserQuery());
                log.info("工具 {} 执行完成", toolName);

                // 记录工具调用历史
                state.getToolCallHistory().add(AgentState.ToolCallRecord.builder()
                        .toolName(toolName)
                        .arguments(arguments)
                        .result(result.length() > 200 ? result.substring(0, 200) + "..." : result)
                        .timestamp(System.currentTimeMillis())
                        .build());

                state.getMessageHistory().add(ToolExecutionResultMessage.from(request, result));

                // 检查是否终止
                if ("doTerminate".equals(toolName)) {
                    state.setFinished(true);
                    state.setFinalAnswer(result);
                }

            } catch (Exception e) {
                log.error("工具执行失败", e);
                state.getMessageHistory().add(ToolExecutionResultMessage.from(
                        request,
                        "工具执行失败: " + e.getMessage()
                ));
            }
        }
    }
}
