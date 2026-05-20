package com.zjw.tools;

import dev.langchain4j.service.tool.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 带限流保护的 ToolExecutor 包装
 * 在真实工具执行前应用并发控制、QPS 限制和熔断保护
 *
 * @author ZhangJw
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitedToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;
    private final String toolName;
    private final ToolRateLimiter rateLimiter;

    @Override
    public String execute(String userQuery, Map<String, Object> parameters) {
        // 1. 尝试获取限流许可
        if (!rateLimiter.tryAcquire(toolName, 3, TimeUnit.SECONDS)) {
            log.warn("工具 {} 限流触发，跳过执行", toolName);
            return String.format("工具 %s 当前调用频率过高，请稍后重试或尝试其他方法", toolName);
        }

        try {
            // 2. 执行真实工具
            String result = delegate.execute(userQuery, parameters);
            rateLimiter.recordSuccess(toolName);
            log.debug("工具 {} 执行成功", toolName);
            return result;

        } catch (Exception e) {
            // 3. 记录失败（熔断统计）
            rateLimiter.recordFailure(toolName);
            log.error("工具 {} 执行失败: {}", toolName, e.getMessage());
            return String.format("工具执行失败: %s，已累计失败 %d 次",
                    e.getMessage(),
                    rateLimiter.getStats(toolName).failCount());
        } finally {
            // 4. 释放信号量
            rateLimiter.release(toolName);
        }
    }
}
