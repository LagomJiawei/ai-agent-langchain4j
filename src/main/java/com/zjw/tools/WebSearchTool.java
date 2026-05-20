package com.zjw.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 网页搜索工具
 * 使用 LangChain4j @Tool 注解
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class WebSearchTool {

    @Tool("搜索网络获取最新信息")
    public String searchWeb(@P("搜索关键词") String query) {
        log.info("执行网页搜索: {}", query);
        try {
            String searchUrl = "https://www.bing.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8");
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            Elements results = doc.select(".b_algo");
            List<String> searchResults = new ArrayList<>();

            int count = 0;
            for (Element result : results) {
                if (count >= 5) break;
                String title = result.select("h2").text();
                String snippet = result.select(".b_caption p").text();
                String url = result.select("a").attr("href");
                searchResults.add(String.format("[%d] %s\n   %s\n   URL: %s",
                        count + 1, title, snippet, url));
                count++;
            }

            if (searchResults.isEmpty()) {
                return "未找到相关搜索结果。";
            }

            return "搜索结果:\n" + String.join("\n\n", searchResults);
        } catch (IOException e) {
            log.error("搜索失败", e);
            return "搜索失败: " + e.getMessage();
        }
    }
}
