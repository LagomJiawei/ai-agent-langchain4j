package com.zjw.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 * 使用 LangChain4j @Tool 注解
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class WebSearchTool {

    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    @Value("${searchapi.api-key}")
    private String apiKey;

    @Value("${searchapi.engine:bing}")
    private String engine;

    @Value("${searchapi.num-results:5}")
    private Integer numResults;

    @Tool("搜索网络获取最新信息")
    public String searchWeb(@P("搜索关键词") String query) {
        log.info("执行网页搜索: {}", query);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", engine);
        paramMap.put("num", numResults);

        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            JSONObject jsonObject = JSONUtil.parseObj(response);

            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            if (organicResults == null || organicResults.isEmpty()) {
                return "未找到相关搜索结果。";
            }

            List<Object> results = organicResults.subList(0, Math.min(numResults, organicResults.size()));

            String result = results.stream().map(obj -> {
                JSONObject tmpJSONObject = (JSONObject) obj;
                return tmpJSONObject.toString();
            }).collect(Collectors.joining("\n\n"));

            return result;
        } catch (Exception e) {
            log.error("搜索失败", e);
            return "搜索失败: " + e.getMessage();
        }
    }
}