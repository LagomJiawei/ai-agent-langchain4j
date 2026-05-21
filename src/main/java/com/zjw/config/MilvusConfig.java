package com.zjw.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Milvus 向量数据库配置
 *
 * @author ZhangJw
 */
@Slf4j
@Configuration
public class MilvusConfig {

    @Resource
    private MilvusProperties milvusProperties;

    @Bean
    @Primary
    @ConditionalOnProperty(name = "milvus.enabled", havingValue = "true")
    public EmbeddingStore<TextSegment> milvusEmbeddingStore() {
        log.info("初始化 Milvus 向量存储: {}:{}", milvusProperties.getHost(), milvusProperties.getPort());
        try {
            EmbeddingStore<TextSegment> store = MilvusEmbeddingStore.builder()
                    .host(milvusProperties.getHost())
                    .port(milvusProperties.getPort())
                    .collectionName(milvusProperties.getCollectionName())
                    .dimension(milvusProperties.getDimension())
                    .metricType(MetricType.COSINE)
                    .indexType(IndexType.valueOf(milvusProperties.getIndexType()))
                    .build();
            log.info("Milvus 向量存储初始化成功");
            return store;
        } catch (Exception e) {
            log.error("Milvus 连接失败，降级使用内存存储: {}", e.getMessage());
            return new InMemoryEmbeddingStore<>();
        }
    }

    @Bean
    @ConditionalOnProperty(name = "milvus.enabled", havingValue = "false", matchIfMissing = true)
    public EmbeddingStore<TextSegment> inMemoryEmbeddingStore() {
        log.info("使用内存向量存储（InMemoryEmbeddingStore）");
        return new InMemoryEmbeddingStore<>();
    }
}
