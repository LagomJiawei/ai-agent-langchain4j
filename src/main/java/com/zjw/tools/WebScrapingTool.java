package com.zjw.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 网页内容抓取工具
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class WebScrapingTool {

    @Tool("抓取指定URL的网页文本内容")
    public String scrapeWebPage(@P("要抓取的网页URL") String url) {
        log.info("抓取网页: {}", url);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();

            String title = doc.title();
            String content = doc.body().text();

            if (content.length() > 5000) {
                content = content.substring(0, 5000) + "\n... (内容过长已截断)";
            }

            return String.format("网页标题: %s\n\n内容:\n%s", title, content);
        } catch (IOException e) {
            log.error("网页抓取失败", e);
            return "网页抓取失败: " + e.getMessage();
        }
    }
}
