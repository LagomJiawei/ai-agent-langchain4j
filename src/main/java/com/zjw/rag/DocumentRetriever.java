package com.zjw.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档检索器 - 增强版
 * 支持双语检索 fallback、结果去重、相关性重排
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class DocumentRetriever {

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private CachingEmbeddingModel cachingEmbeddingModel;

    @Resource
    private RagConfig ragConfig;

    @Resource
    private DocumentReranker documentReranker;

    /**
     * 检索相关文档（支持双语 fallback 和重排序）
     */
    public List<EmbeddingMatch<TextSegment>> retrieve(String query) {
        return retrieve(query, false, true);
    }

    /**
     * 检索相关文档（支持双语 fallback 和重排序）
     */
    public List<EmbeddingMatch<TextSegment>> retrieve(String query, boolean enableBilingualFallback) {
        return retrieve(query, enableBilingualFallback, true);
    }

    /**
     * 检索相关文档（支持双语 fallback 和重排序）
     */
    public List<EmbeddingMatch<TextSegment>> retrieve(String query, boolean enableBilingualFallback, boolean enableRerank) {
        // 1. 主检索（使用已翻译/优化的查询），扩大召回数量为后续重排序做准备
        List<EmbeddingMatch<TextSegment>> mainResults = doRetrieve(query, ragConfig.getRecallK());

        if (!enableBilingualFallback) {
            return enableRerank ? documentReranker.rerank(mainResults, query) : mainResults;
        }

        // 2. 如果结果太少，使用原始查询做 fallback 检索
        if (mainResults.size() < ragConfig.getRecallK() / 2) {
            log.info("主检索结果较少（{} 个），尝试双语 fallback 检索", mainResults.size());
            List<EmbeddingMatch<TextSegment>> fallbackResults = doRetrieve(query, ragConfig.getRecallK());

            // 3. 合并去重
            List<EmbeddingMatch<TextSegment>> merged = mergeDeduplicateAndSort(mainResults, fallbackResults);
            return enableRerank ? documentReranker.rerank(merged, query) : merged;
        }

        return enableRerank ? documentReranker.rerank(mainResults, query) : mainResults;
    }

    /**
     * 执行实际检索
     */
    private List<EmbeddingMatch<TextSegment>> doRetrieve(String query) {
        return doRetrieve(query, ragConfig.getTopK());
    }

    /**
     * 执行实际检索（可指定召回数量）
     */
    private List<EmbeddingMatch<TextSegment>> doRetrieve(String query, int topK) {
        // 使用带缓存的向量化
        Response<Embedding> embeddingResponse = cachingEmbeddingModel.embed(query);
        Embedding queryEmbedding = embeddingResponse.content();

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
                queryEmbedding,
                topK,
                ragConfig.getSimilarityThreshold()
        );

        log.debug("查询 [{}] 检索到 {} 个相关文档片段", query, matches.size());
        return matches;
    }

    /**
     * 合并、去重、排序检索结果
     */
    private List<EmbeddingMatch<TextSegment>> mergeDeduplicateAndSort(
            List<EmbeddingMatch<TextSegment>> list1,
            List<EmbeddingMatch<TextSegment>> list2) {

        // 使用 Map 去重，以文本内容为 key
        Map<String, EmbeddingMatch<TextSegment>> resultMap = new LinkedHashMap<>();

        // 先添加主结果（优先级更高）
        for (EmbeddingMatch<TextSegment> match : list1) {
            String key = getTextHash(match.embedded().text());
            if (!resultMap.containsKey(key) || resultMap.get(key).score() < match.score()) {
                resultMap.put(key, match);
            }
        }

        // 添加 fallback 结果
        for (EmbeddingMatch<TextSegment> match : list2) {
            String key = getTextHash(match.embedded().text());
            if (!resultMap.containsKey(key)) {
                resultMap.put(key, match);
            }
        }

        // 按相似度降序排序并限制数量（使用 recallK 保留更多结果供重排序）
        return resultMap.values().stream()
                .sorted(Comparator.comparingDouble(EmbeddingMatch<TextSegment>::score).reversed())
                .limit(ragConfig.getRecallK())
                .collect(Collectors.toList());
    }

    /**
     * 简单文本哈希用于去重
     */
    private String getTextHash(String text) {
        if (text == null) return "";
        // 取前 100 字符 + 后 100 字符 + 长度
        String normalized = text.replaceAll("\\s+", "").toLowerCase();
        if (normalized.length() <= 200) return normalized;
        return normalized.substring(0, 100) + normalized.substring(normalized.length() - 100);
    }

    /**
     * 检索并格式化上下文（增强版）
     */
    public String retrieveContext(String query) {
        return retrieveContext(query, false);
    }

    /**
     * 检索并格式化上下文（增强版）
     */
    public String retrieveContext(String query, boolean enableBilingualFallback) {
        List<EmbeddingMatch<TextSegment>> matches = retrieve(query, enableBilingualFallback);

        if (matches.isEmpty()) {
            return "无相关文档";
        }

        String context = matches.stream()
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

        log.debug("检索上下文:\n{}", context);
        return context;
    }
}
