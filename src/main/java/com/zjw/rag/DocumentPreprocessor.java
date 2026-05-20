package com.zjw.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档预处理器
 * 负责文档清洗、格式标准化、元数据提取
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class DocumentPreprocessor {

    private static final Pattern TITLE_PATTERN = Pattern.compile("^#{1,3}\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\\n{3,}");

    /**
     * 预处理文档列表
     */
    public List<Document> preprocess(List<Document> rawDocuments) {
        List<Document> processed = new ArrayList<>();
        for (Document doc : rawDocuments) {
            processed.add(preprocessSingle(doc));
        }
        log.info("文档预处理完成: {} 个文档已处理", processed.size());
        return processed;
    }

    /**
     * 预处理单个文档
     */
    public Document preprocessSingle(Document rawDoc) {
        String content = rawDoc.text();
        Metadata metadata = rawDoc.metadata();

        // 1. 清理多余空白和换行
        content = cleanWhitespace(content);

        // 2. 提取标题
        String title = extractTitle(content);
        if (title != null) {
            metadata.put("title", title);
        }

        // 3. 提取章节标题
        List<String> headings = extractHeadings(content);
        if (!headings.isEmpty()) {
            metadata.put("headings", String.join(" | ", headings));
        }

        // 4. 计算文档统计
        metadata.put("word_count", String.valueOf(countWords(content)));
        metadata.put("char_count", String.valueOf(content.length()));

        // 5. 估算 token 数量 (中文每个字约 1 token，英文每个词约 1.3 token)
        metadata.put("estimated_tokens", String.valueOf(estimateTokens(content)));

        return Document.from(content, metadata);
    }

    /**
     * 清理空白字符
     */
    private String cleanWhitespace(String content) {
        // 移除行尾空格
        content = content.replaceAll("\\s+$", "");
        // 合并多个空行
        content = MULTIPLE_NEWLINES.matcher(content).replaceAll("\n\n");
        // 移除 BOM
        content = content.replace("﻿", "");
        return content.trim();
    }

    /**
     * 提取文档标题
     */
    private String extractTitle(String content) {
        Matcher matcher = TITLE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * 提取所有章节标题
     */
    private List<String> extractHeadings(String content) {
        List<String> headings = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(content);
        while (matcher.find() && headings.size() < 10) {
            headings.add(matcher.group(1).trim());
        }
        return headings;
    }

    /**
     * 计算单词数
     */
    private int countWords(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        // 中文字符计数
        int chineseChars = content.codePointAt(0) > 255
                ? (int) content.chars().filter(c -> c > 255).count()
                : 0;
        // 英文单词计数
        String englishPart = content.replaceAll("[^a-zA-Z\\s]", "");
        int englishWords = englishPart.trim().isEmpty()
                ? 0
                : englishPart.trim().split("\\s+").length;
        return chineseChars + englishWords;
    }

    /**
     * 估算 token 数量
     */
    private int estimateTokens(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        // 粗略估算：中文每个字约 1 token，英文每个词约 1.3 token
        int chineseChars = (int) content.chars().filter(c -> c > 255).count();
        int englishChars = content.length() - chineseChars;
        int englishTokens = (int) (englishChars / 4.0); // 英文平均每个词 4 字符，约 1.3 token
        return chineseChars + englishTokens;
    }
}
