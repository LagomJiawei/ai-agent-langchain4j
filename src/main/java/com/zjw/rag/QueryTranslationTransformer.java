package com.zjw.rag;

import dev.langchain4j.model.chat.ChatLanguageModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 查询翻译转换器 - 增强版
 * 支持快速语言检测、高质量翻译、双语检索 fallback
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class QueryTranslationTransformer {

    @Resource
    private ChatLanguageModel chatLanguageModel;

    // 快速语言检测：中文字符 Unicode 范围
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");

    private static final String TRANSLATION_PROMPT = """
            你是专业翻译专家。请将用户查询翻译成中文。

            翻译要求：
            1. 保持专业术语的准确性（如金融、技术词汇）
            2. 保留专有名词和品牌名
            3. 翻译要自然流畅，符合中文表达习惯
            4. 只返回翻译结果，不要添加任何解释

            待翻译文本: %s
            """;

    private static final String ENHANCEMENT_PROMPT = """
            请优化以下中文查询，使其更适合用于文档检索。

            优化规则：
            1. 提取核心关键词
            2. 补充相关专业术语（如果适用）
            3. 去除口语化表达
            4. 保持语义完整，不要改变原意

            原始查询: %s

            只返回优化后的查询文本。
            """;

    /**
     * 主转换方法：检测语言 + 翻译 + 优化查询
     */
    public String transform(String query) {
        if (query == null || query.trim().isEmpty()) {
            return query;
        }

        // 1. 快速语言检测
        LanguageDetectionResult langResult = detectLanguage(query);

        if (langResult.isChinese) {
            // 中文：直接优化查询
            String enhanced = enhanceChineseQuery(query);
            log.info("中文查询优化: {} -> {}", query, enhanced);
            return enhanced;
        } else {
            // 非中文：翻译后优化
            String translated = translateToChinese(query, langResult.confidence);
            String enhanced = enhanceChineseQuery(translated);
            log.info("查询翻译优化: {}({}) -> {}", query, langResult.language, enhanced);
            return enhanced;
        }
    }

    /**
     * 快速语言检测
     */
    private LanguageDetectionResult detectLanguage(String query) {
        // 统计中文字符比例
        long chineseChars = query.chars()
                .filter(c -> c >= '一' && c <= '鿿')
                .count();

        double chineseRatio = (double) chineseChars / query.length();

        if (chineseRatio > 0.3) {
            // 超过 30% 中文字符，判定为中文
            return new LanguageDetectionResult(true, "zh", chineseRatio);
        } else {
            // 简单判定为英文，实际可扩展支持更多语言
            return new LanguageDetectionResult(false, "en", 1.0 - chineseRatio);
        }
    }

    /**
     * 翻译为中文（带错误处理）
     */
    private String translateToChinese(String query, double confidence) {
        try {
            String prompt = String.format(TRANSLATION_PROMPT, query);
            return chatLanguageModel.generate(prompt).trim();
        } catch (Exception e) {
            log.warn("翻译失败，使用原查询: {}", e.getMessage());
            return query;
        }
    }

    /**
     * 优化中文查询，提升检索效果
     */
    private String enhanceChineseQuery(String query) {
        try {
            String prompt = String.format(ENHANCEMENT_PROMPT, query);
            return chatLanguageModel.generate(prompt).trim();
        } catch (Exception e) {
            log.warn("查询优化失败，使用原查询: {}", e.getMessage());
            return query;
        }
    }

    /**
     * 获取原始查询（用于双语检索 fallback）
     * 部分文档可能包含英文内容，原始查询可能检索到更多相关结果
     */
    public String getOriginalQuery(String query) {
        return query;
    }

    /**
     * 语言检测结果
     */
    private static class LanguageDetectionResult {
        final boolean isChinese;
        final String language;
        final double confidence;

        LanguageDetectionResult(boolean isChinese, String language, double confidence) {
            this.isChinese = isChinese;
            this.language = language;
            this.confidence = confidence;
        }
    }
}
