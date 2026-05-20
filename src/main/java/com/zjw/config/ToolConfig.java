package com.zjw.config;

import com.zjw.tools.FileOperationTool;
import com.zjw.tools.PDFGenerationTool;
import com.zjw.tools.RateLimitedToolExecutor;
import com.zjw.tools.ResourceDownloadTool;
import com.zjw.tools.TerminalOperationTool;
import com.zjw.tools.TerminateTool;
import com.zjw.tools.ToolRateLimiter;
import com.zjw.tools.WebScrapingTool;
import com.zjw.tools.WebSearchTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具配置 - LangChain4j
 * 统一管理所有可用工具
 *
 * @author ZhangJw
 */
@Configuration
public class ToolConfig {

    @Resource
    private WebSearchTool webSearchTool;
    @Resource
    private WebScrapingTool webScrapingTool;
    @Resource
    private FileOperationTool fileOperationTool;
    @Resource
    private TerminalOperationTool terminalOperationTool;
    @Resource
    private ResourceDownloadTool resourceDownloadTool;
    @Resource
    private PDFGenerationTool pdfGenerationTool;
    @Resource
    private TerminateTool terminateTool;

    @Resource
    private ToolRateLimiter toolRateLimiter;

    @Value("${tool.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    /**
     * 获取所有工具实例
     */
    @Bean
    public List<Object> allTools() {
        List<Object> tools = new ArrayList<>();
        tools.add(webSearchTool);
        tools.add(webScrapingTool);
        tools.add(fileOperationTool);
        tools.add(terminalOperationTool);
        tools.add(resourceDownloadTool);
        tools.add(pdfGenerationTool);
        tools.add(terminateTool);
        return tools;
    }

    /**
     * 获取工具规范列表 (用于 Agent 初始化)
     */
    @Bean
    public List<ToolSpecification> toolSpecifications() {
        return ToolSpecifications.toolSpecificationsFrom(allTools());
    }

    /**
     * 获取工具执行器映射 (工具名称 -> ToolExecutor)
     * 自动包装限流保护：并发控制、QPS 限制、熔断保护
     */
    @Bean
    public Map<String, ToolExecutor> toolExecutors() {
        List<ToolSpecification> specifications = ToolSpecifications.toolSpecificationsFrom(allTools());

        return specifications.stream()
                .collect(Collectors.toMap(
                        ToolSpecification::name,
                        spec -> {
                            Object tool = findToolByName(spec.name());
                            Method method = findMethodByToolName(tool, spec.name());
                            ToolExecutor executor = new DefaultToolExecutor(tool, method);

                            // 可选包装限流
                            if (rateLimitEnabled) {
                                return new RateLimitedToolExecutor(executor, spec.name(), toolRateLimiter);
                            }
                            return executor;
                        }
                ));
    }

    private Object findToolByName(String toolName) {
        for (Object tool : allTools()) {
            List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(tool);
            for (ToolSpecification spec : specs) {
                if (spec.name().equals(toolName)) {
                    return tool;
                }
            }
        }
        throw new IllegalArgumentException("Tool not found: " + toolName);
    }

    private Method findMethodByToolName(Object tool, String toolName) {
        Method[] methods = tool.getClass().getMethods();
        for (Method method : methods) {
            dev.langchain4j.agent.tool.Tool toolAnnotation =
                    method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
            if (toolAnnotation != null) {
                String methodName = toolAnnotation.name().isEmpty() ? method.getName() : toolAnnotation.name();
                if (methodName.equals(toolName)) {
                    return method;
                }
            }
        }
        throw new IllegalArgumentException("Method not found for tool: " + toolName);
    }
}
