package com.zjw.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.param.MetricType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${milvus.enabled:false}")
    private boolean milvusEnabled;

    @Value("${milvus.host:localhost}")
    private String milvusHost;

    @Value("${milvus.port:19530}")
    private int milvusPort;

    @Value("${milvus.collection-name:rag_documents}")
    private String collectionName;

    @Value("${milvus.dimension:384}")
    private int dimension;

    @Value("${milvus.index-type:IVF_FLAT}")
    private String indexType;

    @Value("${milvus.nprobe:10}")
    private int nprobe;

    @Bean
    @Primary
    @ConditionalOnProperty(name = "milvus.enabled", havingValue = "true")
    public EmbeddingStore<TextSegment> milvusEmbeddingStore() {
        log.info("初始化 Milvus 向量存储: {}:{}", milvusHost, milvusPort);
        try {
            EmbeddingStore<TextSegment> store = MilvusEmbeddingStore.builder()
                    .host(milvusHost)
                    .port(milvusPort)
                    .collectionName(collectionName)
                    .dimension(dimension)
                    .metricType(MetricType.COSINE)
                    .indexType(indexType)
                    .nprobe(nprobe)
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
