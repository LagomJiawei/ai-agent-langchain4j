package com.zjw.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import jakarta.annotation.Resource;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档重排序器
 * 实现多种重排序策略：
 * 1. 混合评分重排序（向量相似度 + BM25 关键词匹配 + 元数据权重）
 * 2. MMR 多样性重排序（Maximal Marginal Relevance）
 * 3. LLM 交叉评分重排序（使用 LLM 判断查询与文档的相关性）
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class DocumentReranker {

    @Resource
    private ChatLanguageModel chatLanguageModel;

    @Resource
    private RagConfig ragConfig;

    /**
     * 重排序策略枚举
     */
    public enum RerankStrategy {
        HYBRID_SCORE,   // 混合评分（默认）
        MMR,            // 最大边际相关性（多样性优化）
        LLM_CROSS_SCORE // LLM 交叉评分
    }

    /**
     * 带重排序分数的匹配结果
     */
    @Data
    @Builder
    public static class RerankedMatch {
        private EmbeddingMatch<TextSegment> original;
        private double vectorScore;
        private double keywordScore;
        private double metadataWeight;
        private double finalScore;
        private double diversityPenalty;
        private String llmReasoning;
    }

    /**
     * 默认重排序（混合评分）
     */
    public List<EmbeddingMatch<TextSegment>> rerank(
            List<EmbeddingMatch<TextSegment>> matches,
            String query) {
        return rerank(matches, query, RerankStrategy.HYBRID_SCORE);
    }

    /**
     * 指定策略重排序
     */
    public List<EmbeddingMatch<TextSegment>> rerank(
            List<EmbeddingMatch<TextSegment>> matches,
            String query,
            RerankStrategy strategy) {

        if (matches == null || matches.isEmpty()) {
            return matches;
        }

        log.info("开始重排序，策略: {}, 初始文档数: {}", strategy, matches.size());

        List<RerankedMatch> reranked;
        switch (strategy) {
            case MMR:
                reranked = rerankByMMR(matches, query);
                break;
            case LLM_CROSS_SCORE:
                reranked = rerankByLLM(matches, query);
                break;
            case HYBRID_SCORE:
            default:
                reranked = rerankByHybridScore(matches, query);
        }

        // 按最终分数排序并转换回原格式
        List<EmbeddingMatch<TextSegment>> result = reranked.stream()
                .sorted(Comparator.comparingDouble(RerankedMatch::getFinalScore).reversed())
                .limit(ragConfig.getTopK())
                .map(RerankedMatch::getOriginal)
                .collect(Collectors.toList());

        log.info("重排序完成，返回 {} 个文档", result.size());
        return result;
    }

    /**
     * 混合评分重排序
     * 公式：finalScore = α * vectorScore + β * keywordScore + γ * metadataWeight
     * 其中 α + β + γ = 1
     */
    private List<RerankedMatch> rerankByHybridScore(
            List<EmbeddingMatch<TextSegment>> matches,
            String query) {

        // 权重配置
        double alpha = 0.5;  // 向量相似度权重
        double beta = 0.35;  // 关键词匹配权重
        double gamma = 0.15; // 元数据权重

        Set<String> queryKeywords = extractKeywords(query);
        log.debug("查询关键词: {}", queryKeywords);

        return matches.stream()
                .map(match -> {
                    TextSegment segment = match.embedded();
                    String text = segment.text();

                    // 1. 向量相似度（已归一化到 [0,1]）
                    double vectorScore = match.score();

                    // 2. BM25 关键词匹配分数
                    double keywordScore = calculateBM25Score(text, queryKeywords);

                    // 3. 元数据权重（标题、章节命中加分）
                    double metadataWeight = calculateMetadataWeight(segment, queryKeywords);

                    // 4. 最终混合分数
                    double finalScore = alpha * vectorScore + beta * keywordScore + gamma * metadataWeight;

                    return RerankedMatch.builder()
                            .original(match)
                            .vectorScore(vectorScore)
                            .keywordScore(keywordScore)
                            .metadataWeight(metadataWeight)
                            .finalScore(finalScore)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * MMR (Maximal Marginal Relevance) 多样性重排序
     * 在相关性和多样性之间取得平衡，避免返回高度相似的文档
     * 公式：MMR = λ * Sim(q, d) - (1 - λ) * max(Sim(d, d_i))
     */
    private List<RerankedMatch> rerankByMMR(
            List<EmbeddingMatch<TextSegment>> matches,
            String query) {

        double lambda = 0.7; // 多样性参数，0.7 是常用值

        List<RerankedMatch> result = new ArrayList<>();
        List<EmbeddingMatch<TextSegment>> remaining = new ArrayList<>(matches);
        Set<String> selectedTexts = new HashSet<>();

        while (!remaining.isEmpty() && result.size() < ragConfig.getTopK()) {
            double bestScore = -1;
            EmbeddingMatch<TextSegment> bestMatch = null;
            double bestDiversityPenalty = 0;

            // 对每个剩余候选计算 MMR 分数
            for (EmbeddingMatch<TextSegment> candidate : remaining) {
                String candidateText = candidate.embedded().text();

                // 1. 相关性分数
                double relevanceScore = candidate.score();

                // 2. 与已选文档的最大相似度
                double maxSimilarity = 0;
                for (String selected : selectedTexts) {
                    double sim = calculateTextSimilarity(candidateText, selected);
                    maxSimilarity = Math.max(maxSimilarity, sim);
                }

                // 3. MMR 分数
                double diversityPenalty = (1 - lambda) * maxSimilarity;
                double mmrScore = lambda * relevanceScore - diversityPenalty;

                if (mmrScore > bestScore) {
                    bestScore = mmrScore;
                    bestMatch = candidate;
                    bestDiversityPenalty = diversityPenalty;
                }
            }

            if (bestMatch != null) {
                remaining.remove(bestMatch);
                selectedTexts.add(bestMatch.embedded().text());
                result.add(RerankedMatch.builder()
                        .original(bestMatch)
                        .vectorScore(bestMatch.score())
                        .diversityPenalty(bestDiversityPenalty)
                        .finalScore(bestScore)
                        .build());
            } else {
                break;
            }
        }

        return result;
    }

    /**
     * LLM 交叉评分重排序
     * 使用 LLM 直接判断查询与文档的相关性，准确率最高但延迟最大
     * 适合对 top-20 结果进行二次精排
     */
    private List<RerankedMatch> rerankByLLM(
            List<EmbeddingMatch<TextSegment>> matches,
            String query) {

        // LLM 重排序成本较高，只对前 N 个结果进行评分
        int topNForLLM = Math.min(20, matches.size());
        List<EmbeddingMatch<TextSegment>> topMatches = matches.subList(0, topNForLLM);

        // 构建 Prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append("请作为相关性评分专家，评估以下文档片段与查询的相关程度。\n\n");
        prompt.append("【查询】：").append(query).append("\n\n");
        prompt.append("【待评分文档片段】：\n\n");

        for (int i = 0; i < topMatches.size(); i++) {
            TextSegment seg = topMatches.get(i).embedded();
            prompt.append(String.format("--- 文档 %d ---\n", i + 1));
            prompt.append("内容：").append(seg.text(), 0, Math.min(300, seg.text().length())).append("\n\n");
        }

        prompt.append("【评分要求】：\n");
        prompt.append("1. 只输出 JSON 数组格式，包含每个文档的 score(0-1) 和 reason(简短理由)\n");
        prompt.append("2. 1 表示完全相关，0 表示完全不相关\n");
        prompt.append("3. 优先考虑：问题能否被该文档片段准确回答\n");
        prompt.append("4. 输出格式示例：[{\"doc\":1,\"score\":0.85,\"reason\":\"包含核心概念定义\"}]\n");

        try {
            String llmResponse = chatLanguageModel.generate(prompt.toString());
            log.debug("LLM 重排序响应: {}", llmResponse);

            // 简单解析（实际生产环境应使用 JSON 解析库）
            Map<Integer, Double> llmScores = parseLLMScoring(llmResponse, topMatches.size());

            List<RerankedMatch> result = new ArrayList<>();
            for (int i = 0; i < topMatches.size(); i++) {
                EmbeddingMatch<TextSegment> match = topMatches.get(i);
                double llmScore = llmScores.getOrDefault(i + 1, match.score());
                // 混合：LLM 分数占 70%，向量分数占 30%
                double finalScore = 0.7 * llmScore + 0.3 * match.score();

                result.add(RerankedMatch.builder()
                        .original(match)
                        .vectorScore(match.score())
                        .finalScore(finalScore)
                        .build());
            }

            return result;

        } catch (Exception e) {
            log.warn("LLM 重排序失败，回退到混合评分: {}", e.getMessage());
            return rerankByHybridScore(matches, query);
        }
    }

    /**
     * 提取查询关键词
     */
    private Set<String> extractKeywords(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptySet();
        }

        // 简单分词 + 去停用词
        String[] words = query.split("[\\s,，.。!！?？;；:：()（）、]+");
        Set<String> stopWords = Set.of(
                "的", "是", "在", "我", "有", "和", "就", "不", "人", "都",
                "一", "一个", "什么", "怎么", "如何", "为什么", "吗", "呢", "吧",
                "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
                "in", "on", "at", "by", "for", "with", "about", "into", "through"
        );

        return Arrays.stream(words)
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(w -> w.length() > 1)
                .filter(w -> !stopWords.contains(w))
                .collect(Collectors.toSet());
    }

    /**
     * 计算 BM25 关键词匹配分数（简化版）
     */
    private double calculateBM25Score(String text, Set<String> keywords) {
        if (keywords.isEmpty()) return 0.5;

        String lowerText = text.toLowerCase();
        int hitCount = 0;
        double totalScore = 0;

        for (String keyword : keywords) {
            int count = countSubstring(lowerText, keyword);
            if (count > 0) {
                hitCount++;
                // 词频加权，考虑长度惩罚
                double tf = 1.0 * count / (count + 0.5 + 1.5 * (text.length() / 200.0));
                totalScore += tf * (1 + Math.log(keyword.length()));
            }
        }

        // 归一化到 [0,1]
        double rawScore = hitCount > 0 ? totalScore / keywords.size() : 0;
        return Math.min(1.0, rawScore);
    }

    /**
     * 计算元数据权重
     * 标题/章节命中关键词有额外加分
     */
    private double calculateMetadataWeight(TextSegment segment, Set<String> keywords) {
        double weight = 0.5; // 基础分

        String title = segment.metadata().getString("title");
        String chapter = segment.metadata().getString("chapter");

        int titleHits = 0;
        int chapterHits = 0;

        if (title != null) {
            String lowerTitle = title.toLowerCase();
            for (String kw : keywords) {
                if (lowerTitle.contains(kw)) titleHits++;
            }
        }

        if (chapter != null) {
            String lowerChapter = chapter.toLowerCase();
            for (String kw : keywords) {
                if (lowerChapter.contains(kw)) chapterHits++;
            }
        }

        // 标题命中有较大权重
        if (titleHits > 0) weight += 0.3 * Math.min(1, titleHits * 0.5);
        if (chapterHits > 0) weight += 0.2 * Math.min(1, chapterHits * 0.5);

        return Math.min(1.0, weight);
    }

    /**
     * 计算文本相似度（基于关键词重叠率）
     */
    private double calculateTextSimilarity(String text1, String text2) {
        Set<String> words1 = extractKeywords(text1);
        Set<String> words2 = extractKeywords(text2);

        if (words1.isEmpty() && words2.isEmpty()) return 1.0;
        if (words1.isEmpty() || words2.isEmpty()) return 0;

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        return 1.0 * intersection.size() / union.size();
    }

    /**
     * 统计子字符串出现次数
     */
    private int countSubstring(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    /**
     * 简单解析 LLM 评分结果
     */
    private Map<Integer, Double> parseLLMScoring(String response, int docCount) {
        Map<Integer, Double> scores = new HashMap<>();

        // 尝试提取 JSON 部分
        try {
            String jsonPart = response;
            int start = response.indexOf("[");
            int end = response.lastIndexOf("]");
            if (start >= 0 && end > start) {
                jsonPart = response.substring(start, end + 1);
            }

            // 简单正则提取 doc 和 score
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "\"doc\"\\s*:\\s*(\\d+)\\s*,\\s*\"score\"\\s*:\\s*([0-9.]+)");
            java.util.regex.Matcher matcher = pattern.matcher(jsonPart);

            while (matcher.find()) {
                int doc = Integer.parseInt(matcher.group(1));
                double score = Double.parseDouble(matcher.group(2));
                scores.put(doc, score);
            }

        } catch (Exception e) {
            log.warn("解析 LLM 评分失败: {}", e.getMessage());
        }

        // 为未命中的文档设置默认分
        for (int i = 1; i <= docCount; i++) {
            if (!scores.containsKey(i)) {
                scores.put(i, 0.5);
            }
        }

        return scores;
    }
}
