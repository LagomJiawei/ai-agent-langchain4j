package com.zjw.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 带 LRU 缓存的 Embedding 模型包装器
 *
 * 目标命中率: >70%
 * 预期收益: 延迟从 ~300ms 降至 ~5ms，API 成本降低 30%
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class CachingEmbeddingModel {

    private final EmbeddingModel delegate;

    // LRU 本地缓存
    private final Map<String, Embedding> localCache;

    // 统计指标
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);

    @Value("${rag.cache.enabled:true}")
    private boolean cacheEnabled;

    public CachingEmbeddingModel(EmbeddingModel delegate) {
        this.delegate = delegate;
        // LRU 缓存：最大 1000 条
        this.localCache = new LinkedHashMap<>(1000, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Embedding> eldest) {
                return size() > 1000;
            }
        };
    }

    /**
     * 单个文本向量化（走缓存）
     */
    public Response<Embedding> embed(String text) {
        totalRequests.incrementAndGet();

        if (!cacheEnabled) {
            return delegate.embed(text);
        }

        String cacheKey = buildCacheKey(text);

        // 1. 查 L1 本地缓存
        Embedding cached;
        synchronized (localCache) {
            cached = localCache.get(cacheKey);
        }
        if (cached != null) {
            cacheHits.incrementAndGet();
            log.debug("L1 缓存命中: {}", text.substring(0, Math.min(30, text.length())));
            return Response.from(cached);
        }

        // 2. 缓存未命中，实时计算
        Response<Embedding> response = delegate.embed(text);

        // 3. 写入 L1 缓存
        synchronized (localCache) {
            localCache.put(cacheKey, response.content());
        }

        return response;
    }

    /**
     * 构建缓存 Key（文本 SHA-256 哈希）
     */
    private String buildCacheKey(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // 降级为简单哈希
            return "emb:" + text.hashCode();
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 获取缓存命中率
     */
    public double getHitRate() {
        long total = totalRequests.get();
        if (total == 0) return 0.0;
        return 1.0 * cacheHits.get() / total;
    }

    /**
     * 获取缓存统计
     */
    public CacheStats getStats() {
        long total = totalRequests.get();
        long hits = cacheHits.get();
        return new CacheStats(
                getHitRate(),
                hits,
                total - hits,
                0,
                localCache.size()
        );
    }

    /**
     * 手动预热缓存
     */
    public void warmUp(List<String> commonQueries) {
        log.info("开始缓存预热，共 {} 个查询", commonQueries.size());
        for (String query : commonQueries) {
            try {
                embed(query);
            } catch (Exception e) {
                log.warn("预热失败: {}", query, e);
            }
        }
        log.info("缓存预热完成，当前缓存大小: {}", localCache.size());
    }

    /**
     * 清空缓存
     */
    public void invalidateAll() {
        synchronized (localCache) {
            localCache.clear();
        }
        totalRequests.set(0);
        cacheHits.set(0);
        log.info("缓存已清空");
    }

    /**
     * 缓存统计
     */
    public static class CacheStats {
        private final double hitRate;
        private final long hitCount;
        private final long missCount;
        private final long evictionCount;
        private final long currentSize;

        public CacheStats(double hitRate, long hitCount, long missCount, long evictionCount, long currentSize) {
            this.hitRate = hitRate;
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.evictionCount = evictionCount;
            this.currentSize = currentSize;
        }

        public double getHitRate() { return hitRate; }
        public long getHitCount() { return hitCount; }
        public long getMissCount() { return missCount; }
        public long getEvictionCount() { return evictionCount; }
        public long getCurrentSize() { return currentSize; }

        @Override
        public String toString() {
            return String.format(
                    "CacheStats{hitRate=%.2f%%, hits=%d, misses=%d, evictions=%d, size=%d}",
                    hitRate * 100, hitCount, missCount, evictionCount, currentSize
            );
        }
    }
}
