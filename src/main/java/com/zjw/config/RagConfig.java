package com.zjw.config;

import com.zjw.rag.DocumentPreprocessor;
import com.zjw.rag.SmartDocumentSplitter;
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

    @Resource
    private RagProperties ragProperties;

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
        File dir = new File(ragProperties.getDocumentDir());
        if (!dir.exists()) {
            dir.mkdirs();
            log.info("RAG 文档目录已创建: {}", ragProperties.getDocumentDir());
            return;
        }

        List<Document> documents = FileSystemDocumentLoader.loadDocumentsRecursively(
                ragProperties.getDocumentDir(),
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

    public void ingestDocuments(List<Document> documents) {
        log.info("开始处理 {} 个文档...", documents.size());

        List<Document> processedDocs = ragProperties.isSmartSplit()
                ? documentPreprocessor.preprocess(documents)
                : documents;

        if (ragProperties.isSmartSplit()) {
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
        return ragProperties.getTopK();
    }

    public double getSimilarityThreshold() {
        return ragProperties.getSimilarityThreshold();
    }

    public int getRecallK() {
        return ragProperties.getRecallK();
    }

    public boolean isRerankEnabled() {
        return ragProperties.isRerankEnabled();
    }

    public String getRerankStrategy() {
        return ragProperties.getRerankStrategy();
    }
}

