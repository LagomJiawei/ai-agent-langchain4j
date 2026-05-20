package com.zjw.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 配置
 *
 * @author ZhangJw
 */
@Slf4j
@Configuration
public class RagConfig {

    @Value("${rag.document-dir:${user.dir}/documents}")
    private String documentDir;

    @Value("${rag.top-k:3}")
    private int topK;

    @Value("${rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${rag.smart-split:true}")
    private boolean smartSplitEnabled;

    @Value("${rag.recall-k:10}")
    private int recallK;

    @Value("${rag.rerank-enabled:true}")
    private boolean rerankEnabled;

    @Value("${rag.rerank-strategy:HYBRID_SCORE}")
    private String rerankStrategy;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private DocumentPreprocessor documentPreprocessor;

    @Resource
    private SmartDocumentSplitter smartDocumentSplitter;

    @PostConstruct
    public void initRagDocuments() {
        File dir = new File(documentDir);
        if (!dir.exists()) {
            dir.mkdirs();
            log.info("RAG 文档目录已创建: {}", documentDir);
            return;
        }

        List<Document> documents = FileSystemDocumentLoader.loadDocumentsRecursively(
                documentDir,
                (Path path) -> path.toString().toLowerCase().endsWith(".txt") ||
                        path.toString().toLowerCase().endsWith(".md") ||
                        path.toString().toLowerCase().endsWith(".pdf")
        );

        if (!documents.isEmpty()) {
            ingestDocuments(documents);
        } else {
            log.info("未找到任何文档文件");
        }
    }

    /**
     * 向量化并存储文档（支持智能预处理和分割）
     */
    public void ingestDocuments(List<Document> documents) {
        log.info("开始处理 {} 个文档...", documents.size());

        // 1. 文档预处理
        List<Document> processedDocs = smartSplitEnabled
                ? documentPreprocessor.preprocess(documents)
                : documents;

        // 2. 文档分割与向量化
        if (smartSplitEnabled) {
            // 智能分割流程
            List<TextSegment> allSegments = new ArrayList<>();
            for (Document doc : processedDocs) {
                String fileName = doc.metadata().getString("file_name");
                if (fileName == null) fileName = "unknown";
                log.info("智能分割文档: {}", fileName);
                List<TextSegment> segments = smartDocumentSplitter.split(doc);
                allSegments.addAll(segments);
            }

            log.info("共生成 {} 个文本分段，开始向量化...", allSegments.size());
            embeddingStore.addAll(embeddingModel.embedAll(allSegments).content(), allSegments);
            log.info("RAG 文档向量化完成，总计 {} 个分段", allSegments.size());
        } else {
            // 传统流程
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(500, 50))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            ingestor.ingest(documents);
            log.info("RAG 文档向量化完成");
        }
    }

    public int getTopK() {
        return topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public int getRecallK() {
        return recallK;
    }

    public boolean isRerankEnabled() {
        return rerankEnabled;
    }

    public String getRerankStrategy() {
        return rerankStrategy;
    }
}

