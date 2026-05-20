package com.zjw.rag;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Redis 分布式 Embedding 缓存
 * 支持 L1 本地内存 + L2 Redis 二级缓存架构
 *
 * @author ZhangJw
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "redis.enabled", havingValue = "true")
public class RedisEmbeddingCache implements EmbeddingModel {

    private static final String CACHE_KEY_PREFIX = "rag:embedding:";
    private static final Duration L2_CACHE_TTL = Duration.ofHours(24);
    private static final int L1_CACHE_SIZE = 2000;

    @Autowired
    private EmbeddingModel delegate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${rag.redis-cache.enabled:true}")
    private boolean redisCacheEnabled;

    // L1 本地内存缓存（LRU）
    private final LinkedHashMapLRU<String, Embedding> localCache = new LinkedHashMapLRU<>(L1_CACHE_SIZE);
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    @Override
    public Response<Embedding> embed(String text) {
        String cacheKey = buildCacheKey(text);

        // L1: 先查本地缓存
        Embedding cached = localCache.get(cacheKey);
        if (cached != null) {
            hitCount.incrementAndGet();
            return Response.from(cached);
        }

        // L2: 查 Redis 缓存
        if (redisCacheEnabled) {
            try {
                String json = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + cacheKey);
                if (json != null && !json.isEmpty()) {
                    float[] vector = objectMapper.readValue(json, float[].class);
                    Embedding embedding = Embedding.from(vector);
                    // 回填 L1 缓存
                    localCache.put(cacheKey, embedding);
                    hitCount.incrementAndGet();
                    return Response.from(embedding);
                }
            } catch (JsonProcessingException e) {
                log.warn("反序列化 Embedding 缓存失败", e);
            }
        }

        // 缓存未命中，调用真实模型
        missCount.incrementAndGet();
        Response<Embedding> response = delegate.embed(text);

        // 回写 L1 和 L2 缓存
        localCache.put(cacheKey, response.content());
        if (redisCacheEnabled) {
            try {
                String json = objectMapper.writeValueAsString(response.content().vector());
                redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + cacheKey, json, L2_CACHE_TTL);
            } catch (JsonProcessingException e) {
                log.warn("序列化 Embedding 缓存失败", e);
            }
        }

        return response;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        // 分离命中和未命中
        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(Collectors.toList());

        List<Embedding> results = new ArrayList<>(texts.size());
        List<Integer> missIndices = new ArrayList<>();

        // 批量查询缓存
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            String cacheKey = buildCacheKey(text);

            Embedding cached = localCache.get(cacheKey);
            if (cached != null) {
                results.add(cached);
                hitCount.incrementAndGet();
            } else {
                results.add(null);
                missIndices.add(i);
                missCount.incrementAndGet();
            }
        }

        // 批量处理未命中的
        if (!missIndices.isEmpty()) {
            List<TextSegment> missSegments = missIndices.stream()
                    .map(textSegments::get)
                    .collect(Collectors.toList());

            Response<List<Embedding>> missResponse = delegate.embedAll(missSegments);
            List<Embedding> missEmbeddings = missResponse.content();

            for (int i = 0; i < missIndices.size(); i++) {
                int originalIdx = missIndices.get(i);
                Embedding emb = missEmbeddings.get(i);
                results.set(originalIdx, emb);

                // 回写缓存
                String cacheKey = buildCacheKey(texts.get(originalIdx));
                localCache.put(cacheKey, emb);

                if (redisCacheEnabled) {
                    try {
                        String json = objectMapper.writeValueAsString(emb.vector());
                        redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + cacheKey, json, L2_CACHE_TTL);
                    } catch (JsonProcessingException e) {
                        // ignore
                    }
                }
            }
        }

        return Response.from(results);
    }

    private String buildCacheKey(String text) {
        return DigestUtil.sha256Hex(text);
    }

    public CacheStats getStats() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (hits * 100.0 / total) : 0;
        return new CacheStats(hitRate, hits, misses, localCache.size());
    }

    public static class CacheStats {
        public final double hitRate;
        public final long hits;
        public final long misses;
        public final int size;

        public CacheStats(double hitRate, long hits, long misses, int size) {
            this.hitRate = hitRate;
            this.hits = hits;
            this.misses = misses;
            this.size = size;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{hitRate=%.1f%%, hits=%d, misses=%d, size=%d}",
                    hitRate, hits, misses, size);
        }
    }

    private static class LinkedHashMapLRU<K, V> extends LinkedHashMap<K, V> {
        private final int maxSize;

        public LinkedHashMapLRU(int maxSize) {
            super(maxSize, 0.75f, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }
}
