package com.zjw.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG 结果语义缓存
 * 语义相似的查询直接返回缓存结果，避免重复检索和 LLM 调用
 * 核心原理：将查询向量化，相似度超过阈值直接返回历史答案
 *
 * @author ZhangJw
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.semantic-cache.enabled", havingValue = "true")
public class RagResultSemanticCache {

    private static final int MAX_CACHE_ITEMS = 1000;
    private static final long CACHE_TTL_SECONDS = 3600 * 24; // 24 小时

    @Autowired
    private EmbeddingModel embeddingModel;

    @Value("${rag.semantic-cache.threshold:0.9}")
    private double similarityThreshold;

    // 缓存的查询向量存储（用于语义匹配）
    private final InMemoryEmbeddingStore<CachedResult> queryEmbeddingStore = new InMemoryEmbeddingStore<>();

    // 结果缓存：queryHash -> CachedResult
    private final Map<String, CachedResult> resultCache = new ConcurrentHashMap<>();

    // LRU 淘汰辅助
    private final LinkedHashMapLRU<String, String> lruOrder = new LinkedHashMapLRU<>(MAX_CACHE_ITEMS);

    /**
     * 尝试从语义缓存获取结果
     *
     * @param query 用户查询
     * @return 缓存的回答，未命中返回 null
     */
    public String get(String query) {
        // 1. 先计算查询向量
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 2. 语义相似度检索
        List<EmbeddingMatch<CachedResult>> matches = queryEmbeddingStore.findRelevant(
                queryEmbedding, 1, similarityThreshold);

        if (!matches.isEmpty()) {
            EmbeddingMatch<CachedResult> match = matches.get(0);
            CachedResult cached = match.embedded();

            // 检查 TTL
            if (Instant.now().getEpochSecond() - cached.getCreatedAt() > CACHE_TTL_SECONDS) {
                // 过期，移除
                removeCache(cached.getQueryHash());
                log.debug("语义缓存已过期: {}", query);
                return null;
            }

            // 命中，更新 LRU 顺序
            lruOrder.touch(cached.getQueryHash());
            log.debug("语义缓存命中（相似度: {}）: {}", match.score(), query);
            return cached.getAnswer();
        }

        return null;
    }

    /**
     * 将结果存入语义缓存
     *
     * @param query  用户查询
     * @param answer 生成的回答
     */
    public void put(String query, String answer) {
        String queryHash = hash(query);

        // 检查是否已存在
        if (resultCache.containsKey(queryHash)) {
            lruOrder.touch(queryHash);
            return;
        }

        // LRU 淘汰
        if (resultCache.size() >= MAX_CACHE_ITEMS) {
            String eldestKey = lruOrder.removeEldest();
            if (eldestKey != null) {
                removeCache(eldestKey);
                log.debug("语义缓存 LRU 淘汰: {}", eldestKey);
            }
        }

        // 向量化并存储
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        CachedResult cached = new CachedResult(queryHash, answer, Instant.now().getEpochSecond());

        queryEmbeddingStore.add(queryEmbedding, cached);
        resultCache.put(queryHash, cached);
        lruOrder.put(queryHash, queryHash);

        log.debug("语义缓存已添加: {}, 缓存大小: {}", query, resultCache.size());
    }

    /**
     * 清除缓存
     */
    public void clear() {
        queryEmbeddingStore.removeAll();
        resultCache.clear();
        lruOrder.clear();
        log.info("语义缓存已清除");
    }

    /**
     * 获取缓存统计
     */
    public CacheStats stats() {
        return new CacheStats(resultCache.size(), MAX_CACHE_ITEMS);
    }

    private String hash(String str) {
        return Integer.toHexString(str.hashCode());
    }

    private void removeCache(String queryHash) {
        resultCache.remove(queryHash);
        // 注意: InMemoryEmbeddingStore 不支持按内容删除，这里简化处理
    }

    @Data
    @AllArgsConstructor
    public static class CachedResult {
        private String queryHash;
        private String answer;
        private long createdAt;
    }

    public record CacheStats(int size, int maxSize) {
        @Override
        public String toString() {
            return String.format("SemanticCache{size=%d, max=%d}", size, maxSize);
        }
    }

    private static class LinkedHashMapLRU<K, V> extends LinkedHashMap<K, V> {
        private final int maxSize;

        public LinkedHashMapLRU(int maxSize) {
            super(maxSize, 0.75f, true);
            this.maxSize = maxSize;
        }

        public void touch(K key) {
            get(key);
        }

        public K removeEldest() {
            if (isEmpty()) return null;
            K eldest = keySet().iterator().next();
            remove(eldest);
            return eldest;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }
}
