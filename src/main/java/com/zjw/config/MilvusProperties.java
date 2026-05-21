package com.zjw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author ZhangJw
 */
@Data
@Component
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {
    private boolean enabled = false;
    private String host = "localhost";
    private int port = 19530;
    private String collectionName = "rag_documents";
    private int dimension = 384;
    private String indexType = "IVF_FLAT";
    private int nprobe = 10;
}