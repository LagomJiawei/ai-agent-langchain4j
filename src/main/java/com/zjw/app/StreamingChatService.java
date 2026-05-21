package com.zjw.app;

import com.zjw.rag.ParallelRagPipelineService;
import com.zjw.rag.RagPipelineService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流式对话服务
 * 使用 SSE（Server-Sent Events）实现 Token 级流式输出
 * 首 Token 响应时间 < 1s
 *
 * @author ZhangJw
 */
@Slf4j
@Service
public class StreamingChatService {

    @Resource
    private StreamingChatLanguageModel streamingChatModel;

    @Resource
    private ParallelRagPipelineService parallelRagPipelineService;

    @Resource
    private RagPipelineService ragPipelineService;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private static final long SSE_TIMEOUT = 60000L;

    /**
     * 普通流式对话
     */
    public SseEmitter streamChat(String query) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        executor.execute(() -> {
            try {
                AtomicLong firstTokenTime = new AtomicLong(0);
                long start = System.currentTimeMillis();
                StringBuilder fullAnswer = new StringBuilder();

                streamingChatModel.generate(query, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        try {
                            if (firstTokenTime.get() == 0) {
                                firstTokenTime.set(System.currentTimeMillis());
                                log.debug("【流式】首 Token 耗时: {}ms", firstTokenTime.get() - start);
                            }
                            fullAnswer.append(token);
                            emitter.send(SseEmitter.event()
                                    .name("token")
                                    .data(token));
                        } catch (IOException e) {
                            log.warn("发送流式数据失败: {}", e.getMessage());
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("complete")
                                    .data("[DONE]"));
                            emitter.complete();
                            log.info("【流式】完成，总耗时: {}ms，输出长度: {}",
                                    System.currentTimeMillis() - start, fullAnswer.length());
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("流式生成失败", error);
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(error.getMessage()));
                        } catch (IOException ignored) {
                        }
                        emitter.completeWithError(error);
                    }
                });

            } catch (Exception e) {
                log.error("流式处理异常", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * RAG 增强流式对话（先检索，后流式生成）
     */
    public SseEmitter streamRagChat(String query, boolean useParallel) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        executor.execute(() -> {
            try {
                long start = System.currentTimeMillis();
                AtomicLong firstTokenTime = new AtomicLong(0);
                StringBuilder fullAnswer = new StringBuilder();

                // 1. 先执行 RAG 检索（同步）
                String context = useParallel
                        ? parallelRagPipelineService.retrieveContextOnly(query)
                        : ragPipelineService.retrieveContextOnly(query);

                long retrievalTime = System.currentTimeMillis() - start;
                log.debug("【流式 RAG】检索完成，耗时: {}ms", retrievalTime);

                // 发送检索完成事件
                emitter.send(SseEmitter.event()
                        .name("retrieval_complete")
                        .data("检索完成，开始生成回答..."));

                // 2. 流式生成答案
                String prompt = String.format(RagPipelineService.GENERATION_PROMPT, context, query);

                streamingChatModel.generate(prompt, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        try {
                            if (firstTokenTime.get() == 0) {
                                firstTokenTime.set(System.currentTimeMillis());
                                log.debug("【流式 RAG】首 Token 耗时: {}ms（检索: {}ms + 生成: {}ms）",
                                        firstTokenTime.get() - start,
                                        retrievalTime,
                                        firstTokenTime.get() - start - retrievalTime);
                            }
                            fullAnswer.append(token);
                            emitter.send(SseEmitter.event()
                                    .name("token")
                                    .data(token));
                        } catch (IOException e) {
                            log.warn("发送流式数据失败: {}", e.getMessage());
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("complete")
                                    .data("[DONE]"));
                            emitter.complete();
                            log.info("【流式 RAG】完成，总耗时: {}ms，输出长度: {}",
                                    System.currentTimeMillis() - start, fullAnswer.length());
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("流式 RAG 生成失败", error);
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(error.getMessage()));
                        } catch (IOException ignored) {
                        }
                        emitter.completeWithError(error);
                    }
                });

            } catch (Exception e) {
                log.error("流式 RAG 处理异常", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
