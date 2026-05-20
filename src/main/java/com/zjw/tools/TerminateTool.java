package com.zjw.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 任务终止工具
 * 用于 Agent 主动结束任务执行
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class TerminateTool {

    @Tool("终止当前任务，当任务完成或无法继续时使用")
    public String doTerminate(@P("终止原因或任务总结") String reason) {
        log.info("任务终止: {}", reason);
        return "TASK_TERMINATED: " + reason;
    }
}
