package com.zjw.controller;

import com.zjw.app.StreamingChatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 流式响应 API 接口
 * 支持 SSE（Server-Sent Events）流式输出
 *
 * API 示例：
 * - GET /api/stream/chat?query=xxx  普通流式对话
 * - GET /api/stream/rag?query=xxx    RAG 增强流式对话
 *
 * @author ZhangJw
 */
@Slf4j
@RestController
@RequestMapping("/api/stream")
public class StreamingController {

    @Resource
    private StreamingChatService streamingChatService;

    /**
     * 普通流式对话
     *
     * 前端使用方式：
     * const eventSource = new EventSource('/api/stream/chat?query=xxx');
     * eventSource.addEventListener('token', e => console.log(e.data));
     * eventSource.addEventListener('complete', () => eventSource.close());
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String query) {
        log.info("【流式 API】普通对话请求: {}", query);
        return streamingChatService.streamChat(query);
    }

    /**
     * RAG 增强流式对话
     *
     * 事件类型：
     * - retrieval_complete: 检索完成通知
     * - token: 每个生成的 Token
     * - complete: 生成完成
     * - error: 发生错误
     */
    @GetMapping(value = "/rag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRagChat(
            @RequestParam String query,
            @RequestParam(defaultValue = "true") boolean useParallel) {
        log.info("【流式 API】RAG 对话请求: {}, parallel={}", query, useParallel);
        return streamingChatService.streamRagChat(query, useParallel);
    }
}
