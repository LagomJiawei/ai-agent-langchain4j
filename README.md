# AI Agent LangChain4j 项目描述

> **版本**: 2.0.0  
> **技术栈**: Spring Boot 3.4.4 + LangChain4j 0.35.0 + Java 21  
> **最后更新**: 2026-05-20

---

## 一、项目概述

### 1.1 项目定位

这是一个**企业级 AI Agent 系统**，基于 LangChain4j 框架，采用 ReAct + Plan-and-Execute 双 Agent 模式，集成 RAG 检索增强生成技术。

系统从 v1.0 升级到 v2.0，新增 5 大生产级特性：
- ✅ **Milvus 向量数据库** - 支持百万级向量检索，可水平扩展
- ✅ **Redis 分布式缓存** - L1 本地内存 + L2 Redis 二级缓存，支持集群部署
- ✅ **RAG 语义结果缓存** - 语义相似查询直接返回结果，降低成本
- ✅ **流式响应 SSE** - 首 Token 响应 < 1s，类 ChatGPT 体验
- ✅ **工具调用并发控制** - 三重保护：并发限制 + QPS 限流 + 熔断

---

## 二、核心功能特性

### 2.1 三种对话模式

| 模式 | 能力 | 适用场景 | API |
|------|------|---------|-----|
| **普通对话** | 多轮记忆、角色设定 | 简单问答 | `GET /api/financial/chat` |
| **RAG 增强** | 查询翻译/重写、向量检索、重排序 | 专业知识问答 | `GET /api/financial/chat/rag` |
| **Agent 模式** | 自主推理、工具调用、循环检测 | 复杂任务处理 | `GET /api/financial/chat/agent` |

### 2.2 Agent 双模式架构

| 模式 | 执行方式 | 平均迭代数 | 适用任务 |
|------|---------|-----------|---------|
| **ReAct** | 思考-行动 循环 | 5-8 次 | 简单快速任务 |
| **Plan-and-Execute** | 先规划，再批量执行 | 2-3 次 | 复杂多步骤任务 |
| **Auto 自动选择** | AgentSelector 智能匹配 | - | 所有场景 |

### 2.3 企业级 RAG 流水线（v2.0）

```
用户查询
  ↓
[语义缓存检查] → 命中直接返回（新增 v2.0）
  ↓
查询翻译 → 中文优化 → 查询重写
  ↓
向量检索（Milvus / InMemory）
  ↓
三层重排序（混合评分 + MMR + LLM 交叉评分）
  ↓
上下文组装
  ↓
LLM 生成
  ↓
[结果写入语义缓存]（新增 v2.0）
```

**缓存架构**：
- **语义缓存** - 相似查询直接返回结果
- **Embedding LRU 缓存** - L1 本地 + L2 Redis 二级缓存

### 2.4 工具生态（7 类 + 限流保护）

| 工具 | 功能 | 限流保护 |
|------|------|---------|
| WebSearchTool | 网页搜索 | ✅ |
| WebScrapingTool | 网页内容抓取 | ✅ |
| FileOperationTool | 文件读写/目录列举 | ✅ |
| TerminalOperationTool | 终端命令执行 | ✅ |
| ResourceDownloadTool | 网络资源下载 | ✅ |
| PDFGenerationTool | 文档生成 | ✅ |
| TerminateTool | 任务终止信号 | ✅ |

**限流机制**（三重保护）：
1. 并发数限制 - 信号量控制同时执行的工具数
2. QPS 限制 - 令牌桶控制每秒调用次数
3. 熔断机制 - 连续失败自动熔断，防止级联失败

### 2.5 流式响应（v2.0 新增）

使用 SSE（Server-Sent Events）实现：
- 首 Token 响应 < 1s
- 逐字输出，类 ChatGPT 体验
- 检索进度通知

**API**：
- `GET /api/stream/chat` - 普通流式对话
- `GET /api/stream/rag` - RAG 增强流式对话

---

## 三、技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.4.4 | 应用框架 |
| LangChain4j | 0.35.0 | LLM 开发框架 |
| Java | 21 | 语言（虚拟线程支持） |
| OpenAI Compatible API | - | 通义千问适配 |
| Milvus | 2.x | 向量数据库（可选） |
| Redis | 7.x | 分布式缓存（可选） |
| Guava | 33.2.1 | 令牌桶限流 |
| Jsoup | 1.19.1 | HTML 解析 |
| Hutool | 5.8.38 | Java 工具库 |

---

## 四、项目结构

```
src/main/java/com/zjw/
├── agent/                          # Agent 框架
│   ├── AgentSelector.java         # 模式自动选择
│   ├── AgentState.java            # 状态管理
│   ├── PlanAndExecuteAgent.java   # 规划执行 Agent
│   └── ReActAgent.java            # ReAct 推理 Agent
├── app/
│   ├── FinancialAdvisorService.java
│   └── StreamingChatService.java  # 流式响应服务（v2.0）
├── chatmemory/                     # 聊天记忆
│   ├── ChatMemoryConfig.java
│   ├── FileChatMemoryStore.java
│   └── RedisChatMemoryStore.java  # Redis 分布式记忆（v2.0）
├── common/
├── config/
│   ├── LlmConfig.java             # 流式模型支持（v2.0）
│   ├── LlmProperties.java
│   ├── MilvusConfig.java          # Milvus 向量库配置（v2.0）
│   ├── RedisConfig.java           # Redis 配置（v2.0）
│   └── ToolConfig.java            # 限流集成（v2.0）
├── controller/
│   ├── FinancialController.java
│   ├── HealthController.java
│   └── StreamingController.java   # 流式 API 接口（v2.0）
├── exception/
├── rag/                           # RAG 流水线
│   ├── CachingEmbeddingModel.java
│   ├── DocumentPreprocessor.java
│   ├── DocumentReranker.java
│   ├── DocumentRetriever.java
│   ├── ParallelRagPipelineService.java  # 并行执行 + 语义缓存
│   ├── QueryRewritingTransformer.java
│   ├── QueryTranslationTransformer.java
│   ├── RagConfig.java
│   ├── RagPipelineService.java
│   ├── RagResultSemanticCache.java  # 语义结果缓存（v2.0）
│   ├── SmartDocumentSplitter.java
│   └── RedisEmbeddingCache.java    # Redis Embedding 缓存（v2.0）
└── tools/
    ├── FileOperationTool.java
    ├── PDFGenerationTool.java
    ├── RateLimitedToolExecutor.java  # 限流执行器包装（v2.0）
    ├── ResourceDownloadTool.java
    ├── TerminalOperationTool.java
    ├── TerminateTool.java
    ├── ToolRateLimiter.java          # 工具限流组件（v2.0）
    ├── WebScrapingTool.java
    └── WebSearchTool.java
```

---

## 五、快速开始

### 5.1 环境准备

```bash
# 1. 配置环境变量
export DASHSCOPE_API_KEY=your-api-key-here

# 2.（可选）启动 Milvus 向量数据库
docker-compose up -d milvus

# 3.（可选）启动 Redis 分布式缓存
docker-compose up -d redis

# 4. 创建知识库文档目录
mkdir -p documents/
# 放入 .txt/.md/.pdf 文档
```

### 5.2 配置文件核心参数

```yaml
# ========== Milvus 向量数据库（可选） ==========
milvus:
  enabled: false          # 开启后替换内存向量库
  host: localhost
  port: 19530

# ========== Redis 分布式缓存（可选） ==========
redis:
  enabled: false          # 开启后支持集群共享
  host: localhost
  port: 6379

# ========== RAG 性能优化 ==========
rag:
  semantic-cache:
    enabled: true         # 语义结果缓存
    threshold: 0.9
  parallel-enabled: true  # 流水线并行执行
  cache-enabled: true     # Embedding LRU 缓存
  redis-cache:
    enabled: true         # Embedding Redis 缓存（需开启 redis.enabled）

# ========== 工具限流保护 ==========
tool:
  rate-limit:
    enabled: true
  limit:
    concurrency: 5        # 单工具最大并发
    qps: 10               # 每秒调用上限
  circuit-breaker:
    fail-threshold: 5     # 连续失败熔断
    recovery-seconds: 30  # 自动恢复时间

# ========== Agent 配置 ==========
agent:
  mode: AUTO               # AUTO / REACT / PLAN_AND_EXECUTE
  max-iterations: 10
```

### 5.3 运行项目

```bash
mvn clean package
java -jar target/ai-agent-langchain4j-2.0.0.jar
```

---

## 六、API 接口完整列表

### 6.1 阻塞式接口

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

### 6.2 流式接口（SSE）

前端 JavaScript 示例：
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

---

## 七、v2.0 性能优化总结

| 优化方向 | 核心实现 | 效果提升 |
|---------|---------|---------|
| **向量库水平扩展** | Milvus 分布式向量库 | 千万级向量检索 < 50ms |
| **分布式缓存** | L1 本地 LRU + L2 Redis | 多实例缓存共享 |
| **语义结果缓存** | 向量相似度匹配 | 重复查询成本降低 90%+ |
| **流式响应** | SSE 逐字输出 | 首 Token 响应 < 1s |
| **工具调用保护** | 并发 + QPS + 熔断 | 防止 API 限流和级联失败 |

---

## 八、扩展开发指南

### 8.1 新增工具

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

### 8.2 替换向量数据库

实现 `EmbeddingStore<TextSegment>` 接口，添加 `@Bean` + `@Primary` 即可

### 8.3 自定义记忆存储

实现 `ChatMemoryStore` 接口，配置 `chat-memory.store-type`

---

## 九、生产部署建议

| 场景 | 推荐配置 |
|------|---------|
| **开发/演示** | Milvus=false, Redis=false |
| **单机生产** | Milvus=true, Redis=false |
| **集群部署** | Milvus=true, Redis=true |

---

