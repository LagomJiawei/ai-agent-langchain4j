package com.zjw.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * 资源下载工具
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class ResourceDownloadTool {

    @Tool("下载网络资源到本地")
    public String downloadResource(@P("资源URL") String url,
                                    @P("保存路径") String savePath) {
        log.info("下载资源: {} -> {}", url, savePath);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                return "下载失败，HTTP 状态码: " + response.statusCode();
            }

            Path path = Paths.get(savePath);
            Files.copy(response.body(), path, StandardCopyOption.REPLACE_EXISTING);

            long fileSize = Files.size(path);
            return String.format("下载成功!\n文件: %s\n大小: %d bytes", savePath, fileSize);
        } catch (Exception e) {
            log.error("下载失败", e);
            return "下载失败: " + e.getMessage();
        }
    }
}
