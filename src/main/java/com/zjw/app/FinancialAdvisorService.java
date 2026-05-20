package com.zjw.app;

import com.zjw.agent.AgentSelector;
import com.zjw.agent.AgentState;
import com.zjw.agent.ReActAgent;
import com.zjw.chatmemory.ChatMemoryConfig;
import com.zjw.rag.CachingEmbeddingModel;
import com.zjw.rag.ParallelRagPipelineService;
import com.zjw.rag.RagPipelineService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 理财顾问服务 - 应用层
 *
 * @author ZhangJw
 */
@Slf4j
@Service
public class FinancialAdvisorService {

    @Resource
    private ChatLanguageModel chatLanguageModel;

    @Resource
    private ChatMemoryConfig chatMemoryConfig;

    @Resource
    private RagPipelineService ragPipelineService;

    @Resource
    private ReActAgent reActAgent;

    @Resource
    private AgentSelector agentSelector;

    @Resource
    private ParallelRagPipelineService parallelRagPipelineService;

    @Resource
    private CachingEmbeddingModel cachingEmbeddingModel;

    @Value("${rag.parallel-enabled:true}")
    private boolean parallelRagEnabled;

    private static final String SYSTEM_PROMPT = """
            你是一位资深理财专家，名叫 "LiCaiManus"。

            你的职责：
            1. 通过提问了解用户的财务状况和需求
            2. 提供个性化的理财建议和规划
            3. 解释复杂的金融概念
            4. 提示投资风险

            回答要求：
            - 专业、严谨、友好
            - 用中文回答
            - 回答有条理，分点说明
            - 如果不确定，诚实地告知用户
            """;

    /**
     * 普通对话 (带记忆)
     */
    public String chat(String message, String chatId) {
        ChatMemory chatMemory = chatMemoryConfig.createChatMemory(chatId);

        // 添加系统消息（首次对话时）
        if (chatMemory.messages().isEmpty()) {
            chatMemory.add(SystemMessage.from(SYSTEM_PROMPT));
        }

        chatMemory.add(UserMessage.from(message));

        Response<AiMessage> response = chatLanguageModel.generate(chatMemory.messages());
        String answer = response.content().text();

        chatMemory.add(AiMessage.from(answer));

        log.info("对话 {} 完成", chatId);
        return answer;
    }

    /**
     * 使用 RAG 知识库对话（支持并行）
     */
    public String chatWithRag(String message, String chatId) {
        return chatWithRag(message, chatId, parallelRagEnabled);
    }

    /**
     * 使用 RAG 知识库对话（可选择并行模式）
     */
    public String chatWithRag(String message, String chatId, boolean useParallel) {
        log.info("RAG 对话 {} (并行={}): {}", chatId, useParallel, message);

        if (useParallel) {
            return parallelRagPipelineService.executeParallel(message);
        } else {
            return ragPipelineService.execute(message);
        }
    }

    /**
     * 使用 Agent 处理任务（自动选择模式）
     */
    public AgentState chatWithAgent(String message) {
        return chatWithAgent(message, false);
    }

    /**
     * 使用 Agent 处理任务（可强制使用 Plan-and-Execute 模式）
     */
    public AgentState chatWithAgent(String message, boolean forcePlanAndExecute) {
        log.info("Agent 处理任务 (Plan-and-Execute={}): {}", forcePlanAndExecute, message);

        if (forcePlanAndExecute) {
            return agentSelector.execute(message);
        } else {
            // 默认使用自动选择模式
            return agentSelector.execute(message);
        }
    }

    /**
     * 获取缓存统计信息
     */
    public CachingEmbeddingModel.CacheStats getCacheStats() {
        return cachingEmbeddingModel.getStats();
    }

    /**
     * 预热 Embedding 缓存
     */
    public void warmUpCache(java.util.List<String> commonQueries) {
        cachingEmbeddingModel.warmUp(commonQueries);
    }

    /**
     * 清空对话记忆
     */
    public void clearChatMemory(String chatId) {
        ChatMemory chatMemory = chatMemoryConfig.createChatMemory(chatId);
        chatMemory.clear();
        log.info("对话 {} 记忆已清空", chatId);
    }
}
