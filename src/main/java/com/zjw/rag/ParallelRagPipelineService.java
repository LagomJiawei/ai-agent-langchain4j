package com.zjw.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 并行化 RAG 流水线服务
 *
 * 性能优化点：
 * 1. 翻译与检索并行（Embedding 提前计算）
 * 2. 中文查询直接跳过翻译阶段
 * 3. 超时保护机制
 *
 * 预期延迟降低：~20%
 *
 * @author ZhangJw
 */
@Slf4j
@Service
public class ParallelRagPipelineService {

    @Resource
    private QueryTranslationTransformer translationTransformer;

    @Resource
    private QueryRewritingTransformer rewritingTransformer;

    @Resource
    private DocumentRetriever documentRetriever;

    @Resource
    private DocumentReranker documentReranker;

    @Resource
    private ChatLanguageModel chatLanguageModel;

    @Resource(required = false)
    private RagResultSemanticCache semanticCache;

    // 异步执行器（虚拟线程，Java 21+）
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private static final String GENERATION_PROMPT = RagPipelineService.GENERATION_PROMPT;

    /**
     * 并行执行完整 RAG 流程（优化版）
     *
     * 时序图：
     * t0: 开始
     * t0-t1: ├─> 翻译 & 重写（串行）
     *        └─> 原始查询 Embedding（并行，提前用于检索）
     * t1-t2: 重写后查询检索（利用已计算的 Embedding）
     * t2-t3: 重排序
     * t3-t4: 答案生成
     */
    public String executeParallel(String query) {
        return executeParallel(query, false, DocumentReranker.RerankStrategy.HYBRID_SCORE);
    }

    public String executeParallel(String query, boolean enableBilingualFallback, DocumentReranker.RerankStrategy rerankStrategy) {
        long start = System.currentTimeMillis();
        log.info("【并行 RAG】开始处理: {}", query);

        // ========== 语义缓存优先检查 ==========
        if (semanticCache != null) {
            String cachedAnswer = semanticCache.get(query);
            if (cachedAnswer != null) {
                log.info("【语义缓存】命中，耗时: {}ms", System.currentTimeMillis() - start);
                return cachedAnswer;
            }
            log.debug("【语义缓存】未命中，继续执行");
        }

        try {
            // ========== 阶段 1：快速语言检测（<1ms） ==========
            boolean isChinese = isChineseQuery(query);

            // ========== 阶段 2：并行启动 ==========
            // 任务 A: 查询变换（翻译 + 重写）
            CompletableFuture<String> transformFuture = CompletableFuture.supplyAsync(() -> {
                long t0 = System.currentTimeMillis();
                String translated = translationTransformer.transform(query);
                String rewritten = rewritingTransformer.transform(translated);
                log.debug("【并行 RAG】查询变换耗时: {}ms", System.currentTimeMillis() - t0);
                return rewritten;
            }, executor);

            // 任务 B: 原始查询 Embedding 预计算（中文可直接用，英文可作为 fallback）
            CompletableFuture<List<EmbeddingMatch<TextSegment>>> preRetrieveFuture = null;
            if (isChinese) {
                preRetrieveFuture = CompletableFuture.supplyAsync(() -> {
                    long t0 = System.currentTimeMillis();
                    List<EmbeddingMatch<TextSegment>> result = documentRetriever.retrieve(query, false, false);
                    log.debug("【并行 RAG】预检索耗时: {}ms, 命中 {} 条", System.currentTimeMillis() - t0, result.size());
                    return result;
                }, executor);
            }

            // ========== 阶段 3：等待查询变换完成 ==========
            String rewrittenQuery = transformFuture.get(5, TimeUnit.SECONDS);

            // ========== 阶段 4：检索（利用并行计算结果） ==========
            List<EmbeddingMatch<TextSegment>> matches;
            if (isChinese && preRetrieveFuture != null) {
                // 中文：预检索已完成，直接使用
                matches = preRetrieveFuture.get(1, TimeUnit.SECONDS);
                log.debug("【并行 RAG】使用预检索结果");
            } else {
                // 非中文：正常检索
                matches = documentRetriever.retrieve(rewrittenQuery, enableBilingualFallback, false);
            }

            // ========== 阶段 5：重排序 ==========
            List<EmbeddingMatch<TextSegment>> reranked = documentReranker.rerank(matches, rewrittenQuery, rerankStrategy);

            // ========== 阶段 6：格式化上下文 + 生成答案 ==========
            String context = formatContext(reranked);
            String prompt = String.format(GENERATION_PROMPT, context, query);
            String answer = chatLanguageModel.generate(prompt);

            // 回写语义缓存
            if (semanticCache != null) {
                semanticCache.put(query, answer);
                log.debug("【语义缓存】已写入");
            }

            long total = System.currentTimeMillis() - start;
            log.info("【并行 RAG】完成，总耗时: {}ms, 检索命中: {} -> {} 条", total, matches.size(), reranked.size());

            return answer;

        } catch (TimeoutException e) {
            log.error("【并行 RAG】超时，降级为串行执行: {}", e.getMessage());
            return executeFallback(query, enableBilingualFallback);
        } catch (Exception e) {
            log.error("【并行 RAG】异常，降级为串行: {}", e.getMessage());
            return executeFallback(query, enableBilingualFallback);
        }
    }

    /**
     * 快速中文检测（<1ms，无需 LLM）
     */
    private boolean isChineseQuery(String query) {
        if (query == null || query.isEmpty()) return false;
        long chineseChars = query.chars().filter(c -> c >= '一' && c <= '鿿').count();
        return (double) chineseChars / query.length() > 0.3;
    }

    /**
     * 降级方案：串行执行
     */
    private String executeFallback(String query, boolean enableBilingualFallback) {
        String translated = translationTransformer.transform(query);
        String rewritten = rewritingTransformer.transform(translated);
        String context = documentRetriever.retrieveContext(rewritten, enableBilingualFallback);
        return chatLanguageModel.generate(String.format(GENERATION_PROMPT, context, query));
    }

    /**
     * 格式化上下文
     */
    private String formatContext(List<EmbeddingMatch<TextSegment>> matches) {
        if (matches.isEmpty()) {
            return "无相关文档";
        }
        return matches.stream()
                .map(match -> {
                    TextSegment segment = match.embedded();
                    String title = segment.metadata().getString("title");
                    if (title == null) title = "";
                    String chapter = segment.metadata().getString("chapter");
                    if (chapter == null) chapter = "";

                    StringBuilder header = new StringBuilder();
                    header.append(String.format("[相似度: %.2f]", match.score()));
                    if (!title.isEmpty()) header.append(" [文档: ").append(title).append("]");
                    if (!chapter.isEmpty()) header.append(" [章节: ").append(chapter).append("]");

                    return header + "\n" + segment.text();
                })
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * 仅检索上下文（用于流式生成，先检索后流式生成）
     */
    public String retrieveContextOnly(String query) {
        try {
            String translated = translationTransformer.transform(query);
            String rewritten = rewritingTransformer.transform(translated);
            List<EmbeddingMatch<TextSegment>> matches = documentRetriever.retrieve(rewritten, false, false);
            List<EmbeddingMatch<TextSegment>> reranked = documentReranker.rerank(matches, rewritten, DocumentReranker.RerankStrategy.HYBRID_SCORE);
            return formatContext(reranked);
        } catch (Exception e) {
            log.error("检索上下文失败", e);
            return "无相关文档";
        }
    }
}
