package com.zjw.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * PDF 生成工具 (简化版本)
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class PDFGenerationTool {

    @Tool("生成文本内容并保存到文件（支持文本格式）")
    public String generateDocument(@P("文件路径") String filePath,
                                    @P("文档标题") String title,
                                    @P("文档内容") String content) {
        log.info("生成文档: {}", filePath);
        try {
            Path path = Paths.get(filePath);

            String markdownContent = String.format("""
                    # %s

                    %s

                    ---
                    *由 AI Agent 自动生成*
                    """, title, content);

            Files.writeString(path, markdownContent, StandardCharsets.UTF_8);
            return "文档生成成功: " + filePath;
        } catch (Exception e) {
            log.error("生成文档失败", e);
            return "生成文档失败: " + e.getMessage();
        }
    }
}
