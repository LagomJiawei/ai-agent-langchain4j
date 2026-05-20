package com.zjw.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * 文件操作工具
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class FileOperationTool {

    @Tool("读取文件内容")
    public String readFile(@P("文件路径") String filePath) {
        log.info("读取文件: {}", filePath);
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "文件不存在: " + filePath;
            }
            String content = Files.readString(path);
            if (content.length() > 10000) {
                return content.substring(0, 10000) + "\n... (文件过大已截断)";
            }
            return content;
        } catch (IOException e) {
            log.error("读取文件失败", e);
            return "读取文件失败: " + e.getMessage();
        }
    }

    @Tool("写入内容到文件")
    public String writeFile(@P("文件路径") String filePath, @P("要写入的内容") String content) {
        log.info("写入文件: {}", filePath);
        try {
            Path path = Paths.get(filePath);
            Files.writeString(path, content,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return "文件写入成功: " + filePath;
        } catch (IOException e) {
            log.error("写入文件失败", e);
            return "写入文件失败: " + e.getMessage();
        }
    }

    @Tool("列出目录内容")
    public String listDirectory(@P("目录路径") String dirPath) {
        log.info("列出目录: {}", dirPath);
        try {
            Path path = Paths.get(dirPath);
            if (!Files.isDirectory(path)) {
                return "不是有效目录: " + dirPath;
            }
            List<String> files = Files.list(path)
                    .limit(50)
                    .map(p -> String.format("%s (%s)",
                            p.getFileName(),
                            Files.isDirectory(p) ? "目录" : "文件"))
                    .toList();
            return "目录 " + dirPath + " 内容:\n" + String.join("\n", files);
        } catch (IOException e) {
            log.error("列出目录失败", e);
            return "列出目录失败: " + e.getMessage();
        }
    }
}
