# Campus RAG 架构路线图

更新时间：2026-05-01

## 1. 项目定位

Campus RAG 是一个基于校园 PDF 文档的智能问答系统。系统目标不是做通用聊天机器人，而是围绕河南工业大学校园资料，提供可检索、可追溯、低幻觉的问答能力。

核心闭环：

```text
PDF 上传 -> 文档落盘 -> 数据库记录 -> 文本解析 -> 语义切片
-> Embedding 向量化 -> 向量检索 -> Prompt 增强 -> SSE 流式回答
```

## 2. 当前架构状态

| 模块 | 技术方案 | 状态 |
| --- | --- | --- |
| 前端 | Vue 3 + Vite + Element Plus | 已完成基础工程 |
| 通信 | REST + SSE | 已完成 |
| 后端 | Java 21 + Spring Boot | 已完成 |
| ORM | MyBatis + MySQL | 已完成 |
| LLM | LangChain4j + DashScope Qwen-Plus | 已完成 |
| 文档解析 | LangChain4j PDFBox Parser + Apache POI Parser | PDF 已完成，Office 文档扩展中 |
| 切片 | `DocumentSplitters.recursive(500, 50)` | 已完成 |
| 向量化 | DashScope `text-embedding-v2` | 已完成 |
| 向量存储 | `InMemoryEmbeddingStore` | 当前阶段继续使用 |
| 启动预热 | `KnowledgeWarmupService` | 已完成 |
| 登录鉴权 | 暂未实现 | 下一阶段 |
| 向量持久化 | 暂未实现 | 后续阶段 |

## 3. 第一阶段：RAG MVP 主链路

状态：已完成。

已完成内容：

- `DocumentServiceImpl`
  - PDF 上传、落盘、数据库状态维护。
- `DocumentIndexingService`
  - PDF 解析、切片、向量化、写入 `EmbeddingStore`。
- `RagService`
  - 问题向量化、Top-K 检索、Prompt 组装。
- `AiChatServiceImpl`
  - 调用 RAG 增强 Prompt 后再流式生成。
- `ChatController`
  - SSE 流式输出，完成时发送 `[DONE]`。
- `KnowledgeWarmupService`
  - 应用启动后重建内存向量库。
- Prompt 模板
  - 外置管理，负责校园助手身份、当前日期、知识库上下文和回答规则。

第一阶段验收标准：

- 上传 PDF 后数据库状态最终变为已完成。
- 服务重启后已完成文档能重新进入内存向量库。
- 提问知识库中存在的问题时，回答能命中文档内容。
- 提问知识库外的问题时，不编造答案。
- SSE 正常结束并发送 `[DONE]`。

## 4. 当前阶段：知识库管理闭环

状态：进行中。

目标：让系统具备可演示的知识库管理能力，而不是只靠接口测试。

范围：

- 前端知识库控制台：
  - 文档总数、可检索、处理中、失败、知识库状态。
  - PDF、Word、Excel、PPT 拖拽上传。
  - 上传进度和错误提示。
  - 文档列表、状态标签、更新时间。
  - 删除前确认。
- 后端文档管理补强：
  - 空文件和非 PDF 文件校验。
  - Word、Excel、PPT 等校园常用文档解析。
  - 上传文件名清洗。
  - 删除数据库记录后尝试清理本地文件。
  - 文档列表按更新时间倒序。
- 聊天页回归：
  - RAG 问答不能被控制台改动破坏。
  - Markdown 展示保持安全、整洁、可读。

当前阶段不做：

- 复杂登录鉴权。
- 多租户知识库隔离。
- Chroma / Milvus / Redis。
- Rerank、Query Rewrite、Intent Classifier。
- 大规模 UI 重构。
- 图片 OCR。图片需要单独的文字识别链路，不与本阶段 Office 文档解析混合。

## 5. 第二阶段：最小角色管理

启动条件：知识库控制台端到端验证通过，并完成阶段提交。

最小目标：

- `ADMIN`：登录后可上传、删除和管理知识库。
- `USER`：登录后只能使用问答。

推荐实现路径：

1. 完善 `user` 表和 `UserMapper`。
2. 密码使用 BCrypt，禁止明文和 MD5。
3. 实现 `AuthController.login/register`。
4. 前端 `Login.vue` 改为真实登录。
5. 前端请求统一携带 Token。
6. 后端文档管理接口限制为管理员操作。

注意：

- 当前不做复杂权限菜单。
- 当前不做用户私有知识库。
- 当前不做组织、学院、班级等复杂身份体系。

## 6. 第三阶段：向量持久化

启动条件：最小角色管理稳定，且内存向量库的重启重建成本开始影响开发或演示。

推荐方案：Chroma。

原因：

- 本项目规模中小，Chroma 成本低。
- LangChain4j 有现成 `ChromaEmbeddingStore`。
- 理论上只需替换 `EmbeddingStoreConfig` 中的 Bean。
- Chroma 上线后，`KnowledgeWarmupService` 可以退役。

当前不提前接入 Chroma，避免在知识库控制台尚未稳定前扩大基础设施复杂度。

## 7. 第四阶段：RAG 质量增强

启动条件：基础业务稳定，且出现明确的回答质量问题。

可选能力：

- 多轮对话记忆
  - 解决“它”“上面那个”等上下文引用。
- Query Rewrite
  - 将口语化问题改写成独立检索问题。
- Rerank
  - 对向量召回结果重新排序。
- Intent Classifier
  - 区分闲聊、知识库问答、系统操作。

这些能力只有在当前问题真实出现时才引入，避免过度工程。

## 8. 架构约束

### 8.1 防过度设计

当前阶段禁止引入：

- RocketMQ、Kafka、Zookeeper
- 分布式锁
- Redis 会话体系
- 复杂权限框架
- 多向量库抽象层

除非当前阶段有明确验收需求，否则不新增基础设施。

### 8.2 Prompt 外置

所有 System Prompt 必须外置到：

```text
src/main/resources/prompts/
```

允许格式：

- `.st`
- `.txt`

禁止在 Service 或 Controller 中硬编码大段 Prompt。

### 8.3 面向接口编程

业务代码注入 LangChain4j 组件时使用接口类型：

- `EmbeddingStore<TextSegment>`
- `EmbeddingModel`

具体实现类只允许出现在配置类中，例如 `EmbeddingStoreConfig`。

### 8.4 MyBatis XML 同步

新增或修改数据库字段时，必须同步：

- Entity
- Mapper interface
- Mapper XML `resultMap`
- INSERT / UPDATE / SELECT SQL

### 8.5 RAG 黄金测试

每次阶段性提交前至少验证：

1. 命中测试
   - 上传校园 PDF。
   - 提问文档中存在的问题。
   - 预期：回答引用知识库内容。
2. 边界测试
   - 提问知识库外问题。
   - 预期：明确说明知识库暂无信息，不编造。
3. 日期测试
   - 提问当前年份。
   - 预期：回答 2026 年。
4. 流式测试
   - 前端能收到完整回答和 `[DONE]`。

## 9. 当前最近任务

1. 修复前端 Vite/Rolldown 中文路径构建问题。
2. 完成知识库控制台端到端验证。
3. 统一 Prompt 目录和代码引用。
4. 准备后端与前端阶段提交。
5. 进入最小角色管理设计。
