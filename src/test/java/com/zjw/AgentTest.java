package com.zjw;

import com.zjw.agent.AgentState;
import com.zjw.agent.ReActAgent;
import com.zjw.rag.RagPipelineService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Agent 测试
 *
 * @author ZhangJw
 */
@Slf4j
@SpringBootTest
public class AgentTest {

    @Resource
    private ReActAgent reActAgent;

    @Resource
    private RagPipelineService ragPipelineService;

    @Test
    public void testAgent() {
        AgentState result = reActAgent.execute("搜索一下今天的天气怎么样");
        log.info("执行结果: {}", result.getFinalAnswer());
        log.info("执行步数: {}", result.getCurrentStep());
        log.info("调用工具数: {}", result.getToolCallHistory().size());
    }

    @Test
    public void testRag() {
        String result = ragPipelineService.execute("什么是指数基金？");
        log.info("RAG 结果: {}", result);
    }

    @Test
    public void testRagWithSteps() {
        RagPipelineService.RagStepResult result = ragPipelineService.executeWithSteps("什么是定投？");
        log.info("原始查询: {}", result.getOriginalQuery());
        log.info("翻译后: {}", result.getTranslatedQuery());
        log.info("重写后: {}", result.getRewrittenQuery());
        log.info("检索上下文: {}", result.getRetrievedContext());
        log.info("最终答案: {}", result.getFinalAnswer());
    }
}
