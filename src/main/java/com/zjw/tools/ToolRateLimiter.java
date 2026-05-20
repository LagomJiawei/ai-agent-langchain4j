package com.zjw.tools;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工具调用并发控制器
 *
 * 三重保护机制：
 * 1. 并发数限制 (Semaphore) - 同时执行的工具数
 * 2. QPS 限制 (RateLimiter) - 每秒调用次数
 * 3. 熔断机制 - 连续失败达到阈值自动熔断
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class ToolRateLimiter {

    // 工具并发限制
    private final Map<String, Semaphore> semaphores = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    // 熔断统计
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    @Value("${tool.limit.concurrency:5}")
    private int defaultConcurrency;

    @Value("${tool.limit.qps:10}")
    private double defaultQps;

    @Value("${tool.circuit-breaker.fail-threshold:5}")
    private int failThreshold;

    @Value("${tool.circuit-breaker.recovery-seconds:30}")
    private int recoverySeconds;

    /**
     * 尝试获取执行许可（阻塞等待）
     *
     * @param toolName 工具名称
     * @return 是否获取成功
     */
    public boolean tryAcquire(String toolName) {
        return tryAcquire(toolName, 5, TimeUnit.SECONDS);
    }

    /**
     * 尝试获取执行许可（带超时）
     */
    public boolean tryAcquire(String toolName, long timeout, TimeUnit unit) {
        // 1. 检查熔断状态
        CircuitBreaker cb = circuitBreakers.computeIfAbsent(toolName, k -> new CircuitBreaker());
        if (cb.isOpen()) {
            log.warn("【工具限流】工具 {} 已熔断，跳过执行", toolName);
            return false;
        }

        // 2. QPS 限流（非阻塞）
        RateLimiter rateLimiter = rateLimiters.computeIfAbsent(toolName, k -> RateLimiter.create(defaultQps));
        if (!rateLimiter.tryAcquire(timeout, unit)) {
            log.debug("【工具限流】QPS 超限: {}", toolName);
            return false;
        }

        // 3. 并发限制（阻塞等待）
        Semaphore semaphore = semaphores.computeIfAbsent(toolName, k -> new Semaphore(defaultConcurrency, true));
        try {
            boolean acquired = semaphore.tryAcquire(timeout, unit);
            if (!acquired) {
                log.warn("【工具限流】并发数超限: {}", toolName);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放执行许可（执行完成后调用）
     */
    public void release(String toolName) {
        Semaphore semaphore = semaphores.get(toolName);
        if (semaphore != null) {
            semaphore.release();
        }
    }

    /**
     * 记录工具执行成功
     */
    public void recordSuccess(String toolName) {
        CircuitBreaker cb = circuitBreakers.get(toolName);
        if (cb != null) {
            cb.onSuccess();
        }
    }

    /**
     * 记录工具执行失败
     */
    public void recordFailure(String toolName) {
        CircuitBreaker cb = circuitBreakers.get(toolName);
        if (cb != null) {
            cb.onFailure();
        }
    }

    /**
     * 获取工具当前统计信息
     */
    public ToolStats getStats(String toolName) {
        Semaphore semaphore = semaphores.get(toolName);
        CircuitBreaker cb = circuitBreakers.get(toolName);
        return new ToolStats(
                semaphore != null ? semaphore.availablePermits() : defaultConcurrency,
                cb != null ? cb.failCount.get() : 0,
                cb != null ? cb.state : CircuitBreaker.State.CLOSED
        );
    }

    /**
     * 断路器内部类
     */
    private class CircuitBreaker {
        enum State { CLOSED, OPEN, HALF_OPEN }

        final AtomicLong failCount = new AtomicLong(0);
        volatile State state = State.CLOSED;
        volatile long openTime = 0;

        boolean isOpen() {
            if (state == State.OPEN) {
                // 检查是否过了恢复时间
                if (System.currentTimeMillis() - openTime > recoverySeconds * 1000L) {
                    state = State.HALF_OPEN;
                    failCount.set(0);
                    log.info("【工具熔断】{} 进入半开状态，尝试恢复", toolName);
                }
            }
            return state == State.OPEN;
        }

        void onSuccess() {
            failCount.set(0);
            if (state == State.HALF_OPEN) {
                state = State.CLOSED;
                log.info("【工具熔断】{} 已关闭", toolName);
            }
        }

        void onFailure() {
            long count = failCount.incrementAndGet();
            if (count >= failThreshold && state == State.CLOSED) {
                state = State.OPEN;
                openTime = System.currentTimeMillis();
                log.warn("【工具熔断】{} 连续失败 {} 次，已熔断，{} 秒后恢复",
                        toolName, failThreshold, recoverySeconds);
            }
        }
    }

    public record ToolStats(int availablePermits, long failCount, CircuitBreaker.State state) {
        @Override
        public String toString() {
            return String.format("ToolStats{permits=%d, fails=%d, state=%s}",
                    availablePermits, failCount, state);
        }
    }
}
