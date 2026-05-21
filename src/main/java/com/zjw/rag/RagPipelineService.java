package com.zjw.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 流水线服务
 * 完整流程: 查询翻译 -> 查询重写 -> 文档检索 -> 答案生成
 *
 * @author ZhangJw
 */
@Slf4j
@Service
public class RagPipelineService {

    @Resource
    private QueryTranslationTransformer translationTransformer;

    @Resource
    private QueryRewritingTransformer rewritingTransformer;

    @Resource
    private DocumentRetriever documentRetriever;

    @Resource
    private ChatLanguageModel chatLanguageModel;

    @Resource
    private DocumentReranker documentReranker;

    public static final String GENERATION_PROMPT = """
            你是一位专业的理财顾问。请基于以下参考资料回答用户的问题。

            【参考资料】
            %s

            【用户问题】
            %s

            回答要求：
            1. 只能基于参考资料回答，不要编造信息
            2. 如果参考资料中没有相关信息，明确告知用户无法回答
            3. 回答要专业、准确、有条理
            4. 用中文回答
            """;

    /**
     * 执行完整的 RAG 流程
     */
    public String execute(String query) {
        return execute(query, true);
    }

    /**
     * 执行完整的 RAG 流程（可配置双语 fallback）
     */
    public String execute(String query, boolean enableBilingualFallback) {
        return execute(query, enableBilingualFallback, DocumentReranker.RerankStrategy.HYBRID_SCORE);
    }

    /**
     * 执行完整的 RAG 流程（可配置双语 fallback 和重排序策略）
     */
    public String execute(String query, boolean enableBilingualFallback, DocumentReranker.RerankStrategy rerankStrategy) {
        log.info("开始 RAG 流程: {}, 重排序策略: {}", query, rerankStrategy);

        // 1. 查询翻译 + 优化
        String translatedQuery = translationTransformer.transform(query);

        // 2. 查询重写（关键词优化）
        String rewrittenQuery = rewritingTransformer.transform(translatedQuery);

        // 3. 文档检索（先关闭内置重排序，手动指定策略）
        List<EmbeddingMatch<TextSegment>> matches = documentRetriever.retrieve(
                rewrittenQuery, enableBilingualFallback, false);

        // 4. 手动执行重排序（支持策略选择）
        List<EmbeddingMatch<TextSegment>> reranked = documentReranker.rerank(matches, rewrittenQuery, rerankStrategy);

        // 5. 格式化上下文
        String context = formatContext(reranked);

        // 6. 生成答案
        String prompt = String.format(GENERATION_PROMPT, context, query);
        String answer = chatLanguageModel.generate(prompt);

        log.info("RAG 流程完成");
        return answer;
    }

    /**
     * 仅检索上下文（用于流式生成，先检索后流式生成）
     */
    public String retrieveContextOnly(String query) {
        String translated = translationTransformer.transform(query);
        String rewritten = rewritingTransformer.transform(translated);
        List<EmbeddingMatch<TextSegment>> matches = documentRetriever.retrieve(rewritten, false, false);
        List<EmbeddingMatch<TextSegment>> reranked = documentReranker.rerank(matches, rewritten, DocumentReranker.RerankStrategy.HYBRID_SCORE);
        return formatContext(reranked);
    }

    /**
     * 格式化检索结果为上下文字符串
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
     * 分步执行 RAG（用于调试）
     */
    public RagStepResult executeWithSteps(String query) {
        RagStepResult result = new RagStepResult();
        result.setOriginalQuery(query);

        // Step 1: 翻译
        result.setTranslatedQuery(translationTransformer.transform(query));

        // Step 2: 重写
        result.setRewrittenQuery(rewritingTransformer.transform(result.getTranslatedQuery()));

        // Step 3: 检索
        result.setRetrievedContext(documentRetriever.retrieveContext(result.getRewrittenQuery()));

        // Step 4: 生成
        String prompt = String.format(GENERATION_PROMPT, result.getRetrievedContext(), query);
        result.setFinalAnswer(chatLanguageModel.generate(prompt));

        return result;
    }

    /**
     * RAG 分步执行结果
     */
    public static class RagStepResult {
        private String originalQuery;
        private String translatedQuery;
        private String rewrittenQuery;
        private String retrievedContext;
        private String finalAnswer;

        // getter and setter
        public String getOriginalQuery() { return originalQuery; }
        public void setOriginalQuery(String originalQuery) { this.originalQuery = originalQuery; }
        public String getTranslatedQuery() { return translatedQuery; }
        public void setTranslatedQuery(String translatedQuery) { this.translatedQuery = translatedQuery; }
        public String getRewrittenQuery() { return rewrittenQuery; }
        public void setRewrittenQuery(String rewrittenQuery) { this.rewrittenQuery = rewrittenQuery; }
        public String getRetrievedContext() { return retrievedContext; }
        public void setRetrievedContext(String retrievedContext) { this.retrievedContext = retrievedContext; }
        public String getFinalAnswer() { return finalAnswer; }
        public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }
    }
}
