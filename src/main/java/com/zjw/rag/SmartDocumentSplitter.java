package com.zjw.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.Metadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能文档分割器
 * 支持语义感知、章节边界优先、重叠优化、上下文注入
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class SmartDocumentSplitter {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern PARAGRAPH_BOUNDARY = Pattern.compile("\\n\\s*\\n");

    private final int maxSegmentTokens;
    private final int overlapTokens;
    private final DocumentSplitter defaultSplitter;

    public SmartDocumentSplitter() {
        this(500, 100); // 默认 500 token 每段，重叠 100 token（增强上下文连贯性）
    }

    public SmartDocumentSplitter(int maxSegmentTokens, int overlapTokens) {
        this.maxSegmentTokens = maxSegmentTokens;
        this.overlapTokens = overlapTokens;
        this.defaultSplitter = DocumentSplitters.recursive(maxSegmentTokens, overlapTokens);
    }

    /**
     * 智能分割文档
     */
    public List<TextSegment> split(Document document) {
        String content = document.text();
        Metadata metadata = document.metadata();

        // 1. 短文档直接返回
        if (estimateTokens(content) <= maxSegmentTokens) {
            TextSegment segment = TextSegment.from(content, metadata.copy());
            segment.metadata().put("segment_index", "0");
            segment.metadata().put("segment_total", "1");
            return List.of(segment);
        }

        // 2. 优先按章节边界分割
        List<TextSegment> segments = splitByChapters(content, metadata);

        // 3. 如果章节分割失败，回退到递归分割
        if (segments.isEmpty()) {
            segments = defaultSplitter.split(document);
            // 为分割后的片段添加索引元数据
            for (int i = 0; i < segments.size(); i++) {
                segments.get(i).metadata().put("segment_index", String.valueOf(i));
                segments.get(i).metadata().put("segment_total", String.valueOf(segments.size()));
            }
        }

        // 4. 为每个分段注入上下文（文档标题、父章节）
        enrichSegmentsWithContext(segments, document);

        log.info("文档分割完成: {} 个分段", segments.size());
        return segments;
    }

    /**
     * 按章节边界分割
     */
    private List<TextSegment> splitByChapters(String content, Metadata metadata) {
        List<ChapterBoundary> boundaries = findChapterBoundaries(content);

        if (boundaries.isEmpty()) {
            return List.of();
        }

        List<TextSegment> segments = new ArrayList<>();
        String currentChapterTitle = "";
        int segmentIndex = 0;

        for (int i = 0; i < boundaries.size(); i++) {
            ChapterBoundary boundary = boundaries.get(i);
            int start = boundary.start;
            int end = (i < boundaries.size() - 1) ? boundaries.get(i + 1).start : content.length();

            String chapterContent = content.substring(start, end);
            currentChapterTitle = boundary.title;

            // 检查章节长度
            if (estimateTokens(chapterContent) <= maxSegmentTokens * 1.5) {
                // 章节长度合适，作为一个分段
                Metadata segmentMetadata = metadata.copy();
                segmentMetadata.put("chapter", currentChapterTitle);
                segmentMetadata.put("segment_index", String.valueOf(segmentIndex++));
                segments.add(TextSegment.from(chapterContent.trim(), segmentMetadata));
            } else {
                // 章节太长，按段落进一步分割
                List<TextSegment> subSegments = splitByParagraphs(chapterContent, currentChapterTitle, metadata);
                for (TextSegment seg : subSegments) {
                    seg.metadata().put("segment_index", String.valueOf(segmentIndex++));
                }
                segments.addAll(subSegments);
            }
        }

        // 添加总分段数元数据
        for (TextSegment seg : segments) {
            seg.metadata().put("segment_total", String.valueOf(segments.size()));
        }

        return segments;
    }

    /**
     * 按段落分割
     */
    private List<TextSegment> splitByParagraphs(String content, String chapterTitle, Metadata baseMetadata) {
        List<TextSegment> segments = new ArrayList<>();
        String[] paragraphs = PARAGRAPH_BOUNDARY.split(content);

        StringBuilder currentSegment = new StringBuilder();
        int currentTokens = 0;

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            int paraTokens = estimateTokens(para);

            if (currentTokens + paraTokens > maxSegmentTokens && currentTokens > 0) {
                // 当前分段已满，保存并开始新分段
                Metadata meta = baseMetadata.copy();
                meta.put("chapter", chapterTitle);
                segments.add(TextSegment.from(currentSegment.toString().trim(), meta));
                currentSegment = new StringBuilder();
                currentTokens = 0;
            }

            if (currentSegment.length() > 0) {
                currentSegment.append("\n\n");
            }
            currentSegment.append(para);
            currentTokens += paraTokens;
        }

        // 添加最后一个分段
        if (currentTokens > 0) {
            Metadata meta = baseMetadata.copy();
            meta.put("chapter", chapterTitle);
            segments.add(TextSegment.from(currentSegment.toString().trim(), meta));
        }

        // 添加重叠内容（前一段的末尾）
        addOverlaps(segments);

        return segments;
    }

    /**
     * 添加重叠内容增强上下文连贯性
     */
    private void addOverlaps(List<TextSegment> segments) {
        if (segments.size() <= 1) return;

        for (int i = 1; i < segments.size(); i++) {
            TextSegment prev = segments.get(i - 1);
            TextSegment curr = segments.get(i);

            String prevText = prev.text();
            int overlapChars = Math.min(overlapTokens * 2, prevText.length() / 3); // 粗略估算
            String overlapContent = prevText.substring(prevText.length() - overlapChars).trim();

            // 将前一段的尾部添加到当前段开头
            String newText = "[前情回顾: ..." + overlapContent + "]\n\n" + curr.text();
            segments.set(i, TextSegment.from(newText, curr.metadata()));
        }
    }

    /**
     * 查找章节边界
     */
    private List<ChapterBoundary> findChapterBoundaries(String content) {
        List<ChapterBoundary> boundaries = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(content);

        while (matcher.find()) {
            String levelStr = matcher.group(1);
            String title = matcher.group(2).trim();
            int level = levelStr.length();

            // 只考虑 H1-H3 作为主要分割边界
            if (level <= 3) {
                boundaries.add(new ChapterBoundary(matcher.start(), level, title));
            }
        }

        return boundaries;
    }

    /**
     * 为分段注入上下文元数据
     */
    private void enrichSegmentsWithContext(List<TextSegment> segments, Document document) {
        String docTitle = document.metadata().getString("title");
        if (docTitle == null) docTitle = "";

        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            if (!docTitle.isEmpty()) {
                // 将文档标题注入到分段开头，提升检索相关性
                String newText = String.format("【文档: %s】%n%s", docTitle, segment.text());
                segments.set(i, TextSegment.from(newText, segment.metadata()));
            }
        }
    }

    /**
     * 估算 token 数量
     */
    private int estimateTokens(String content) {
        if (content == null || content.isEmpty()) return 0;

        // 中文字符每个约 1 token
        int chineseChars = (int) content.chars().filter(c -> c > 255).count();
        // 英文每 4 字符约 1 token
        int englishChars = content.length() - chineseChars;
        int englishTokens = englishChars / 4;

        return chineseChars + englishTokens;
    }

    /**
     * 章节边界内部类
     */
    private static class ChapterBoundary {
        final int start;
        final int level;
        final String title;

        ChapterBoundary(int start, int level, String title) {
            this.start = start;
            this.level = level;
            this.title = title;
        }
    }
}
