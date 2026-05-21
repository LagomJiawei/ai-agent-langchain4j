# AI Agent LangChain4j - LiCaiManus 理财顾问

> **版本**: 1.0.0  
> **技术栈**: Spring Boot 3.4.4 + LangChain4j 0.35.0 + Java 21  
> **最后更新**: 2026-05-21

---

## 项目简介

**LiCaiManus** 是一个企业级 AI Agent 系统，基于 LangChain4j 框架构建，采用 ReAct + Plan-and-Execute 双 Agent 模式，集成 RAG 检索增强生成技术，为用户提供智能化的理财咨询服务。

## ✨ 核心特性

### 🤖 智能 Agent 系统
- **ReAct 推理框架**: 思考 → 行动 → 观察 循环迭代
- **Plan-and-Execute 架构**: 先规划再执行，迭代次数减少 50%
- **Agent 自动选择器**: 根据任务复杂度智能匹配最佳模式
- **循环防御机制**: 防止死循环和重复工具调用

### 📚 企业级 RAG 流水线
```
用户查询
  ↓
[语义缓存检查] → 命中直接返回
  ↓
查询翻译 → 中文优化 → 查询重写
  ↓
向量检索（Milvus / InMemory）
  ↓
三层重排序（混合评分 + MMR + LLM 交叉评分）
  ↓
上下文组装 → LLM 生成
```

### 🔧 工具生态（7 类 + 限流保护）
| 工具 | 功能 | 限流保护 |
|------|------|---------|
| WebSearchTool | 网页搜索（Bing） | ✅ |
| WebScrapingTool | 网页内容抓取 | ✅ |
| FileOperationTool | 文件读写/目录列举 | ✅ |
| TerminalOperationTool | 终端命令执行 | ✅ |
| ResourceDownloadTool | 网络资源下载 | ✅ |
| PDFGenerationTool | 文档生成 | ✅ |
| TerminateTool | 任务终止信号 | ✅ |

**限流机制（三重保护）**:
1. 并发数限制 - 信号量控制
2. QPS 限制 - 令牌桶算法
3. 熔断机制 - 连续失败自动熔断

### 💾 多级缓存架构
- **L1 本地 LRU 缓存**: Embedding 计算结果缓存
- **L2 Redis 分布式缓存**: 多实例缓存共享
- **语义结果缓存**: 相似查询直接返回结果

### 🚀 流式响应（SSE）
- 首 Token 响应 < 1s
- 逐字输出，类 ChatGPT 体验
- 检索进度实时通知

## 🛠 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.4.4 | 应用框架 |
| LangChain4j | 0.35.0 | LLM 开发框架 |
| Java | 21 | 语言（虚拟线程支持） |
| 通义千问 | - | LLM（兼容 OpenAI 接口） |
| Milvus | 2.x | 向量数据库（生产环境） |
| Redis | 7.x | 分布式缓存 |
| Guava | 33.2.1 | 令牌桶限流 |
| Jsoup | 1.19.1 | HTML 解析 |
| Hutool | 5.8.38 | Java 工具库 |

## 📁 项目结构

```
src/main/java/com/zjw/
├── agent/                          # Agent 框架
│   ├── AgentSelector.java         # 模式自动选择
│   ├── AgentState.java            # 状态管理
│   ├── PlanAndExecuteAgent.java   # 规划执行 Agent
│   └── ReActAgent.java            # ReAct 推理 Agent
├── app/
│   ├── FinancialAdvisorService.java
│   └── StreamingChatService.java  # 流式响应服务
├── chatmemory/                     # 聊天记忆
│   ├── ChatMemoryConfig.java
│   ├── FileChatMemoryStore.java
│   └── RedisChatMemoryStore.java  # Redis 分布式记忆
├── config/
│   ├── LlmConfig.java             # 流式模型支持
│   ├── MilvusConfig.java          # Milvus 向量库配置
│   ├── RedisConfig.java           # Redis 配置
│   └── ToolConfig.java            # 限流集成
├── controller/
│   ├── FinancialController.java
│   ├── HealthController.java
│   └── StreamingController.java   # 流式 API 接口
├── rag/                           # RAG 流水线
│   ├── CachingEmbeddingModel.java
│   ├── DocumentPreprocessor.java
│   ├── DocumentReranker.java
│   ├── DocumentRetriever.java
│   ├── ParallelRagPipelineService.java
│   ├── QueryRewritingTransformer.java
│   ├── QueryTranslationTransformer.java
│   ├── RagPipelineService.java
│   ├── RagResultSemanticCache.java
│   ├── SmartDocumentSplitter.java
│   └── RedisEmbeddingCache.java
└── tools/
    ├── FileOperationTool.java
    ├── PDFGenerationTool.java
    ├── RateLimitedToolExecutor.java
    ├── ResourceDownloadTool.java
    ├── TerminalOperationTool.java
    ├── TerminateTool.java
    ├── ToolRateLimiter.java
    ├── WebScrapingTool.java
    └── WebSearchTool.java
```

## 🚀 快速开始

### 1. 环境准备

```bash
# 配置 API Key
export DASHSCOPE_API_KEY=your-api-key-here
export BING_SEARCH_API_KEY=your-bing-key-here

# （可选）启动 Milvus 向量数据库
docker-compose up -d milvus

# （可选）启动 Redis 分布式缓存
docker-compose up -d redis

# 创建知识库文档目录
mkdir -p documents/
# 放入 .txt/.md/.pdf 文档
```

### 2. 核心配置

```yaml
# application.yml
milvus:
  enabled: false          # 开启后替换内存向量库
  host: localhost
  port: 19530

redis:
  enabled: false          # 开启后支持集群共享
  host: localhost
  port: 6379

rag:
  semantic-cache:
    enabled: true
    threshold: 0.9
  parallel-enabled: true

tool:
  rate-limit:
    enabled: true
  limit:
    concurrency: 5
    qps: 10

agent:
  mode: AUTO               # AUTO / REACT / PLAN_AND_EXECUTE
  max-iterations: 10
```

### 3. 运行项目

```bash
mvn clean package
java -jar target/ai-agent-langchain4j-1.0.0.jar
```

## 🔌 API 接口

### 阻塞式接口

```bash
# 普通对话
GET /api/financial/chat?query=xxx&chatId=123

# RAG 增强对话
GET /api/financial/chat/rag?query=xxx&chatId=123

# Agent 复杂任务
GET /api/financial/chat/agent?query=xxx

# 清空记忆
POST /api/financial/memory/clear?chatId=123

# 健康检查
GET /health
```

### 流式接口（SSE）

```javascript
const eventSource = new EventSource('/api/stream/rag?query=什么是基金');

eventSource.addEventListener('retrieval_complete', e => {
    console.log('检索完成，开始生成...');
});

eventSource.addEventListener('token', e => {
    process.stdout.write(e.data);  // 逐字输出
});

eventSource.addEventListener('complete', () => {
    eventSource.close();
    console.log('\n生成完成');
});
```

## 📊 部署建议

| 场景 | 推荐配置 |
|------|---------|
| **开发/演示** | Milvus=false, Redis=false |
| **单机生产** | Milvus=true, Redis=false |
| **集群部署** | Milvus=true, Redis=true |

## 🏗 架构优势

| 优化方向 | 核心实现 | 效果提升 |
|---------|---------|---------|
| **向量库水平扩展** | Milvus 分布式向量库 | 千万级向量检索 < 50ms |
| **分布式缓存** | L1 本地 LRU + L2 Redis | 多实例缓存共享 |
| **语义结果缓存** | 向量相似度匹配 | 重复查询成本降低 90%+ |
| **流式响应** | SSE 逐字输出 | 首 Token 响应 < 1s |
| **工具调用保护** | 并发 + QPS + 熔断 | 防止 API 限流和级联失败 |

## 📝 扩展开发

### 新增工具

```java
@Component
public class MyCustomTool {
    @Tool("工具功能描述")
    public String doSomething(@P("参数说明") String param) {
        // 工具逻辑
        return "result";
    }
}
```

自动集成：限流、并发控制、熔断保护

### 替换向量数据库

实现 `EmbeddingStore<TextSegment>` 接口，添加 `@Bean` + `@Primary` 即可

---

**License**: MIT
