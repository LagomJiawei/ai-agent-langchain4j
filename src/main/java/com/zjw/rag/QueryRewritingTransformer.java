package com.zjw.rag;

import dev.langchain4j.model.chat.ChatLanguageModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 查询重写转换器
 * 优化查询表达，提取核心关键词，提高检索准确率
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class QueryRewritingTransformer {

    @Resource
    private ChatLanguageModel chatLanguageModel;

    private static final String PROMPT = """
            你是专业的搜索查询优化专家。请对用户的查询进行优化，使其更适合向量检索。

            优化原则：
            1. 提取核心关键词，去除无关词汇（如"请问"、"我想了解一下"等）
            2. 补充隐含的专业术语和相关概念
            3. 保持语义不变，但表达更简洁明确
            4. 如果是复杂问题，拆分为多个子查询

            示例：
            输入: "我想了解一下那种跟着大盘走的基金是什么"
            输出: 指数基金 定义 特点 交易方式 费率

            现在请优化以下查询：
            %s

            只输出优化后的查询文本，不要有任何解释。
            """;

    public String transform(String query) {
        String prompt = String.format(PROMPT, query);
        String result = chatLanguageModel.generate(prompt).trim();
        log.info("查询重写: {} -> {}", query, result);
        return result;
    }
}
