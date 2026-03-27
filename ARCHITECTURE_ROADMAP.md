# 🏛️ Campus RAG 智能问答系统：架构演进与未来开发规划书
> **项目定位**：基于 RAG 技术解决河南工业大学校园非结构化文档检索与大模型幻觉问题的智能问答系统。
> **文档版本**：v1.0 · 2026-03-27
> **作者**：首席架构师 (Claude Code) · 项目负责人 (开发者)

---

## 目录

1. [第一部分：当前架构快照与 MVP 收尾计划](#part1)
2. [第二部分：第二阶段 — 业务闭环与持久化演进](#part2)
3. [第三部分：第三阶段 — 高级 RAG 特性与高可用架构](#part3)
4. [第四部分：架构师开发军规](#part4)

---

## Part 1：当前架构快照与 MVP 收尾计划 <a name="part1"></a>

### 1.1 技术栈全景

| 层次 | 技术选型 | 状态 |
|------|---------|------|
| **前端** | Vue 3 (Composition API) + Vite + Element Plus | ✅ 已完成 |
| **通信** | HTTP REST + SSE (Server-Sent Events) | ✅ 已完成 |
| **后端框架** | Java 21 + Spring Boot 3 | ✅ 已完成 |
| **ORM** | MyBatis + MySQL 8 | ✅ 已完成 |
| **LLM 接入** | LangChain4j 0.35.0 + 通义千问 Qwen-Plus (DashScope) | ✅ 已完成 |
| **PDF 解析** | LangChain4j Apache PDFBox Parser | ✅ 已完成 |
| **文本切片** | `DocumentSplitters.recursive(500, 50)` | ✅ 已完成 |
| **向量化** | DashScope `text-embedding-v2` (1536维) | ✅ 已完成 |
| **向量存储** | `InMemoryEmbeddingStore` (单例 Bean) | ✅ 已完成 |
| **认证** | 无（暂时跳过） | ⏳ Phase 2 |
| **向量持久化** | 无（内存方案，重启丢失） | ⏳ Phase 2 |

### 1.2 当前架构拓扑

```
┌─────────────────────────────────────────────────────────────┐
│                        前端 (Vue 3 + Vite)                    │
│   Chat.vue ──SSE──► ChatMessage.vue (Markdown + 打字机动画)   │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTP / SSE
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                     后端 (Spring Boot 3)                      │
│                                                               │
│  DocumentController ──► DocumentServiceImpl                   │
│       │ POST /upload         │                                │
│       │                      ▼  摄入管道（同步）               │
│       │                 ┌─────────────────────────────┐       │
│       │                 │ (a) 物理落盘 (D:/uploads)    │       │
│       │                 │ (b) DB 初始化 status=1       │       │
│       │                 │ (c) PDFBox 解析 + 切片       │       │
│       │                 │ (d) text-embedding-v2 向量化 │       │
│       │                 │ (e) DB status=2 收尾         │       │
│       │                 └──────────────┬──────────────┘       │
│       │                                │                       │
│       │                                ▼                       │
│       │                 ┌──────────────────────────────┐      │
│       │                 │  InMemoryEmbeddingStore       │      │
│       │                 │  (Spring 单例 Bean)           │      │
│       │                 └──────────────────────────────┘      │
│       │                                                        │
│  ChatController ──► AiChatServiceImpl                          │
│    GET /stream           │  [⚠️ 当前：直接透传给 LLM]           │
│                          │  [⏳ 目标：先 RAG 检索再增强]         │
│                          ▼                                     │
│                   通义千问 Qwen-Plus API                        │
└─────────────────────────────────────────────────────────────┘
                             │
                             ▼
                      MySQL 8 (campusrag)
                      user / document 表
```

> ⚠️ **当前核心缺口**：ChatController → AiChatServiceImpl 这条链路目前是"纯 LLM 透传"，RAG 检索尚未接入。这是 MVP 阶段最后的攻坚目标。

---

### 1.3 MVP 收尾：剩余 3 步行动指南

以下三步是下次开发的**唯一优先级**，严格按序执行：

---

#### 🔷 Step 6：新建 `RagService` — 系统的"检索大脑"

**文件**：`service/RagService.java` + `service/impl/RagServiceImpl.java`

**核心职责**：
1. 接收用户原始问题 `userQuestion`
2. 调用 `EmbeddingModel` 将问题向量化
3. 调用 `InMemoryEmbeddingStore.findRelevant()` 检索 Top-K（建议 K=5）相关 Chunk
4. 将检索结果拼装为结构化的 System Prompt（注入当前日期 + 河工大人设 + 知识库内容）
5. 返回增强后的完整 Prompt 字符串

**System Prompt 模板**（需外置为 `resources/prompts/answer-chat-kb.txt`）：
```
你是河南工业大学的校园智能助手，今天是 {currentDate}。
你只能基于以下从知识库中检索到的内容回答用户问题：

--- 知识库内容 ---
{retrievedContext}
--- END ---

【回答规则】
1. 严格基于知识库内容作答，禁止编造或猜测
2. 知识库无相关信息时，回复："我目前的知识库中暂无此信息，建议联系学校相关部门确认。"
3. 使用简洁中文，适当使用列表格式提升可读性
4. 不暴露内部文档结构（章节编号、页码等）
```

---

#### 🔷 Step 7：改造 `AiChatServiceImpl` — RAG 融合

**文件**：`service/impl/AiChatServiceImpl.java`

**改造要点**：
- 注入 `RagService`
- 在 `streamChatWithAi(userMessage)` 中，先调用 `ragService.buildAugmentedPrompt(userMessage)` 获取增强后的完整 Prompt
- 将增强 Prompt 作为 `SystemMessage`，原始 `userMessage` 作为 `UserMessage`，组装 `List<ChatMessage>` 后调用流式模型

---

#### 🔷 Step 8：双项改造 `ChatController` + Step 9：冷启动预热

**ChatController 改造**：
- `SseEmitter(0L)` 替换固定的 `60000L`，防止长回答被超时截断
- 在 `StreamingResponseHandler.onComplete()` 回调中发送 `[DONE]` 事件，与前端 `Chat.vue` 的拦截逻辑精确对接

**新建 `KnowledgeWarmupService`**（Step 9）：
```java
@EventListener(ApplicationReadyEvent.class)
public void warmup() {
    // 扫描 document 表中 status=2 的记录
    // 从磁盘重新加载 PDF → 解析 → 切片 → 向量化 → 填充 InMemoryEmbeddingStore
    // 解决服务重启后向量库数据清空的问题
}
```

---

## Part 2：第二阶段 — 业务闭环与持久化演进 <a name="part2"></a>

> **启动条件**：RAG 核心链路端到端验证通过（能上传 PDF、提问、得到知识库引用答案）。

### 2.1 用户体系：从"假登录"到真实鉴权

#### 当前状态
- `AuthController` 已有框架，`login/register` 方法体为空
- `DocumentController` 通过 `X-User-Id` Header 传递用户 ID，默认硬编码为 `1L`
- 前端 `Login.vue` 点击即跳转，无真实校验

#### 演进路径

**方案推荐：Sa-Token（轻量，已被 ragent 验证）**

> Sa-Token 比 Spring Security 配置更简洁，适合中小型项目，且 ragent 已有完整的实践案例可参考。

```
Step A：引入 Sa-Token 依赖 + Redis（可选，支持内存模式）
Step B：实现 UserService.login()：校验用户名/密码 → BCrypt 验证 → 颁发 Token
Step C：实现 UserService.register()：用户名去重 → BCrypt 加密 → 插入 DB
Step D：在 ChatController / DocumentController 上添加 @SaCheckLogin 注解
Step E：前端 Login.vue 改为真实 POST /api/auth/login，存储 Token 到 localStorage
Step F：前端 Axios 请求拦截器统一添加 Authorization Header
```

**密码存储规范**：必须使用 BCrypt，禁止明文或 MD5。

---

### 2.2 数据隔离：用户-文档权限绑定

当前 `document` 表已有 `user_id` 字段，架构已预留，改动成本极低：

| 接口 | 当前（无鉴权） | 改造后（鉴权）|
|------|--------------|-------------|
| `POST /upload` | 硬编码 `userId=1` | 从 Sa-Token 上下文获取当前登录用户 ID |
| `GET /documents` | 返回所有文档 | 只返回 `WHERE user_id = {currentUserId}` |
| `DELETE /{id}` | 无鉴权删除 | 校验文档归属权再删除，非本人返回 403 |
| RAG 检索 | 全局检索 | **按用户隔离检索**（需在 Chunk 元数据中写入 userId，检索时过滤） |

> **关键挑战**：`InMemoryEmbeddingStore` 不支持元数据过滤检索。此问题在迁移向量库（2.3节）后天然解决。

---

### 2.3 向量持久化：InMemoryEmbeddingStore 替换方案

#### 当前方案的致命缺陷
- 服务重启 → 向量库清空 → 必须重新向量化所有文档（冷启动耗时 O(n)）
- 无法按用户 ID 过滤检索
- 数据量大时内存压力上升

#### 替换方案对比

| 方案 | 适用场景 | 改动成本 | 依赖 |
|------|---------|---------|------|
| **Chroma** (推荐 Phase 2) | 中小型项目，易上手 | 低 | Docker 1 个容器 |
| **Milvus** | 大规模生产，高性能 | 中 | Docker Compose 多容器 |
| **pgvector** | 已有 PostgreSQL 时 | 低 | PostgreSQL 插件 |

#### 推荐迁移路径（Chroma）

```yaml
# docker-compose.yml（新增）
services:
  chroma:
    image: chromadb/chroma:latest
    ports:
      - "8000:8000"
    volumes:
      - chroma_data:/chroma/chroma

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: campusrag
      MYSQL_ROOT_PASSWORD: 030926
    ports:
      - "3306:3306"
```

**代码改动成本极低**：LangChain4j 提供 `ChromaEmbeddingStore`，只需替换 `EmbeddingStoreConfig` 中的 Bean 声明，`RagService` 和 `DocumentServiceImpl` 的调用代码**零改动**（面向接口编程的红利）：

```java
// 仅需修改 EmbeddingStoreConfig.java
@Bean
public EmbeddingStore<TextSegment> embeddingStore() {
    // 从 InMemoryEmbeddingStore 换为 ChromaEmbeddingStore
    return ChromaEmbeddingStore.builder()
        .baseUrl("http://localhost:8000")
        .collectionName("campus-rag")
        .build();
}
```

同时，`KnowledgeWarmupService` 可**退役**，冷启动问题从根本上消除。

---

## Part 3：第三阶段 — 高级 RAG 特性与高可用架构 <a name="part3"></a>

> **启动条件**：第二阶段稳定运行，用户体系与向量持久化均已落地。
> **战略意义**：提升回答质量与系统深度，作为毕设答辩的技术亮点展示。

### 3.1 多轮对话记忆（Session Memory）

**现状问题**：每次问答独立，AI 无法理解"它"、"上面说的"等代词引用。

**方案**：基于 Redis 的滑动窗口对话历史

```
架构设计：
用户提问 → 从 Redis 读取最近 N 轮历史 → 与当前问题一起构造对话列表
              → RAG 检索（用改写后的问题）→ 流式回答 → 将本轮 QA 写入 Redis
```

**实现要点**：
- Redis Key 设计：`conversation:{userId}:{sessionId}` → List 结构
- 滑动窗口：保留最近 4-6 轮（可配置），防止 Token 超限
- TTL：60 分钟无活动自动过期
- **引入时机**：向量库持久化完成后，Redis 已作为基础设施存在，顺势实现

---

### 3.2 检索效果增强

#### Query Rewrite（查询重写）

**解决的问题**：
- 用户提问口语化、含代词（"它的开放时间是？"）
- 问题与文档描述方式不匹配（语义鸿沟）

**实现方式**：在 `RagService.buildAugmentedPrompt()` 前增加一个 LLM 调用步骤，将原始问题改写为更规范、去除代词的独立问题。（参考 ragent 的 `user-question-rewrite.st` 模板）

**引入时机**：多轮对话记忆上线后，代词消解需求自然出现。

---

#### Rerank（检索后重排）

**解决的问题**：向量相似度召回的 Top-K 结果不一定按相关性最优排序。

**实现方式**：在 `embeddingStore.findRelevant()` 之后，调用 DashScope 的 `qwen3-rerank` 模型对 Top-K 结果重新评分和排序。

**引入时机**：当 RAG 回答中出现"引用了不相关内容"的用户投诉时，说明召回精度已成为瓶颈，此时引入。

---

### 3.3 多通道路由：意图识别（Intent Classifier）

**解决的问题**：区分用户的"闲聊"与"知识库问答"，避免所有请求都走 RAG 管道（浪费 Embedding 调用）。

**设计方案**：

```
用户提问
    │
    ▼
Intent Classifier（轻量 LLM 调用，Temperature=0.1）
    │
    ├── 意图：CHITCHAT（闲聊）
    │       └──► 直接调用 LLM，不走 RAG
    │
    ├── 意图：KB_QUERY（知识库问答）
    │       └──► 走完整 RAG 管道
    │
    └── 意图：SYSTEM（系统操作，如"帮我查我的文档列表"）
            └──► 路由到特定 Controller 方法
```

**分类模型**：可直接用 Qwen-Plus 配合少样本 Prompt 实现，无需单独训练分类模型。

**引入时机**：系统日活用户增加，Embedding API 费用成为可感知开销时。

---

## Part 4：架构师开发军规 <a name="part4"></a>

> 以下准则是基于本项目实际情况制定的约束，旨在防止架构偏航、降低维护成本。
> **所有开发者（包括 AI 协作者）均须严格遵守。**

---

### 🔒 Rule 1：防过度设计（YAGNI 原则）

> "You Aren't Gonna Need It" — 不要为假设的未来需求写代码。

- **禁止**在当前阶段引入 RocketMQ、Kafka、Zookeeper 等消息中间件
- **禁止**在无高并发证明前设计分布式锁、限流器
- 每次引入新依赖，必须能在当前 Issue 中找到**具体的使用场景**
- 参考决策：我们分析了 ragent 的完整技术栈后，**主动拒绝了 7 项组件**

---

### 🔒 Rule 2：Prompt 模板必须外置

> 将 Prompt 写死在 Java 字符串中，是 RAG 项目最常见的架构债务。

- 所有 System Prompt 模板必须存放于 `src/main/resources/prompts/` 目录
- 文件格式：`.txt` 或 `.st`（StringTemplate），支持 `{variable}` 占位符替换
- **禁止**在任何 Service/Controller 中硬编码 Prompt 字符串
- 原因：Prompt 调优频率远高于代码迭代，外置模板无需重新编译部署

---

### 🔒 Rule 3：面向接口编程，为替换留口

> 当前使用 `InMemoryEmbeddingStore`，未来会替换为 Chroma/Milvus。

- `EmbeddingStore` / `EmbeddingModel` 等 LangChain4j 接口，注入时**必须使用接口类型**，不得使用具体实现类
- `EmbeddingStoreConfig` 是唯一允许出现具体实现类名的文件
- 遵守此规则后，向量库迁移的改动范围将严格限制在 **1 个配置文件**内

---

### 🔒 Rule 4：数据库字段变更必须同步 XML Mapper

> MyBatis XML Mapper 不会因字段新增而自动报错，只会在运行时静默失败。

- 新增 Entity 字段 → 必须同步更新对应的 `resultMap` 和 INSERT/UPDATE SQL
- 删除字段前 → 必须先搜索 Mapper XML 中的所有引用
- 建议为每次 Schema 变更维护增量 `migration_xxx.sql` 文件

---

### 🔒 Rule 5：RAG 质量的黄金测试标准

> "系统能启动"≠"RAG 有效"。每次迭代后必须通过以下验收测试：

```
测试用例 1（命中测试）：
  上传《学生手册.pdf》→ 提问"请假需要哪些手续？"
  预期：AI 回答引用了文档中的具体条款内容

测试用例 2（边界测试）：
  提问"今天股市怎么样？"
  预期：AI 回复"知识库中暂无此信息"，而非编造答案

测试用例 3（日期感知测试）：
  提问"今天是哪一年？"
  预期：AI 能回答"2026年"，而非模型训练截止年份
```

---

### 🔒 Rule 6：前端代码约束（Vue 3）

- 严格使用 `<script setup>` 语法，**禁止** Options API
- **禁止**修改 `variables.css`、全局路由骨架与 `MainLayout`/`BlankLayout` 布局框架
- 新功能在现有 `views/` 和 `components/` 中扩展，不新建顶层目录
- SSE 通信统一走 `EventSource` → 完成后发送 `[DONE]` 信号关闭

---

*📌 本文档应随项目演进持续更新。每完成一个阶段里程碑，请更新对应章节的状态标记。*

---

**文档结束** · Campus RAG 项目 · 河南工业大学 · 2026
