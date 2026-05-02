# Campus RAG 架构路线图

更新时间：2026-05-03

## 1. 项目定位

Campus RAG 是一个面向河南工业大学校园资料的智能问答系统。系统目标不是通用聊天，而是围绕学校制度、通知、表格、办事指南等资料，提供可检索、低幻觉、可演示的问答能力。

当前核心闭环：

```text
校园资料上传 -> 文件落盘 -> document 记录 -> 文本解析 -> 语义切片
-> Embedding 向量化 -> 向量检索 -> Prompt 增强 -> SSE 流式回答
```

当前已支持的资料类型：

- PDF
- Word：`.doc`、`.docx`
- Excel：`.xls`、`.xlsx`
- PowerPoint：`.ppt`、`.pptx`

图片资料需要 OCR，不属于当前普通文档解析链路。

## 2. 当前架构状态

| 模块 | 技术方案 | 状态 |
| --- | --- | --- |
| 前端 | Vue 3 + Vite 7.2.7 + Element Plus | 已完成基础工程和知识库控制台 |
| 通信 | REST + SSE | 已完成 |
| 后端 | Java 21 + Spring Boot | 已完成 |
| ORM | MyBatis + MySQL | 已完成 |
| LLM | LangChain4j + DashScope Qwen-Plus | 已完成 |
| 文档解析 | PDFBox Parser + Apache POI Parser | PDF / Office 已接入 |
| 切片 | `DocumentSplitters.recursive(500, 50)` | 已完成 |
| 向量化 | DashScope `text-embedding-v2` | 已完成 |
| 向量存储 | `InMemoryEmbeddingStore` | 当前阶段继续使用 |
| 启动预热 | `KnowledgeWarmupService` | 已完成 |
| Prompt | `src/main/resources/prompts/` | 已统一外置 |
| 登录鉴权 | 暂未实现 | 下一阶段 |
| 向量持久化 | 暂未实现 | 角色管理之后评估 |
| OCR | 暂未实现 | 后续独立阶段 |

## 3. 第一阶段：RAG MVP 主链路

状态：已完成。

已完成内容：

- `DocumentServiceImpl`
  - 文档上传、文件落盘、数据库状态维护。
  - 支持 PDF、Word、Excel、PPT。
  - 保存短扩展名到 `file_type`，避免 Office MIME 超长导致 MySQL 截断。
  - 删除数据库记录后尝试清理本地文件。
- `DocumentIndexingService`
  - 根据扩展名选择 PDFBox 或 POI 解析器。
  - 完成解析、切片、向量化、写入 `EmbeddingStore`。
- `RagService`
  - 问题向量化、Top-K 检索、Prompt 组装。
- `AiChatServiceImpl`
  - 调用 RAG 增强 Prompt 后再流式生成。
- `ChatController`
  - SSE 流式输出，完成时发送 `[DONE]`。
- `KnowledgeWarmupService`
  - 应用启动后扫描已完成文档，重建内存向量库。
- Prompt 模板
  - 外置管理，负责校园助手身份、当前日期、知识库上下文和回答规则。

验收状态：

- 文档上传后数据库状态最终变为已完成。
- 服务重启后已完成文档可通过预热恢复到内存向量库。
- 提问知识库中存在的问题时，回答能命中文档内容。
- SSE 正常结束并发送 `[DONE]`。

## 4. 第二阶段：知识库管理闭环

状态：第一版已完成，正在补测收口。

目标：让系统具备可演示、可验证的知识库管理能力，而不是只靠接口测试。

已完成范围：

- 前端知识库控制台：
  - 文档总数、可检索、处理中、失败、知识库状态。
  - PDF、Word、Excel、PPT 拖拽上传。
  - 上传进度和错误提示。
  - 文档列表、状态标签、更新时间、搜索。
  - 删除前确认。
  - Claude 风格工作台视觉收敛。
- 后端文档管理：
  - 空文件和不支持类型校验。
  - Word、Excel、PPT 等校园常用文档解析。
  - 上传文件名清洗。
  - 删除数据库记录后尝试清理本地文件。
  - 文档列表按更新时间倒序。
- 聊天页回归：
  - RAG 问答未被控制台改动破坏。
  - Markdown 展示保持安全、整洁、可读。

已验证结果：

- `/dashboard` 可通过前端代理拉取 `GET /api/documents`。
- 当前知识库返回 7 条文档。
- 最新 `.docx` 文档 `status=2`、`fileType=docx`。
- 问题“校级三好学生审批表的格式是什么”能命中文档内容，并正常收到 `[DONE]`。
- PDF、DOCX、XLSX、PPTX 上传接口已完成补测；无效 PPTX 会进入失败状态。
- 删除接口已验证可删除数据库记录并清理本地文件。
- 删除向量残留修复已完成运行态验证：删除 DOCX/PPTX 后立即提问对应 token，不再命中。
- 重启预热验证通过：历史 PDF 和名单文档在 Spring Boot 重启后仍可命中。
- 浏览器 UI 搜索和删除确认弹窗已验证通过。

补测清单：

1. 已完成：上传测试，覆盖 PDF、DOCX、XLSX、PPTX。
2. 已完成：列表接口测试，覆盖状态、类型标签、更新时间倒序。
3. 已完成：删除接口测试，覆盖数据库记录删除和本地文件清理。
4. 已完成：命中测试，覆盖 DOCX、XLSX、PPTX 和 PDF token。
5. 已完成：边界测试，知识库外问题不编造答案。
6. 已完成：删除后立即提问被删除文档内容，不再命中。
7. 已完成：后端重启后预热恢复检索能力。
8. 已完成：浏览器 UI 搜索和删除确认弹窗。
9. 已完成：真实中文 PDF 抽取和 RAG 命中验证。
10. 待复核：在本机 IDEA 或普通 PowerShell 补跑后端单元测试。

当前发现并修复的闭环缺口：

- 删除文档后，当前运行中的 `InMemoryEmbeddingStore` 会残留已删除文档切片。
- 修复策略：索引切片已有 `documentId` 元数据，删除文档时通过 `MetadataFilterBuilder.metadataKey("documentId")` 移除对应向量切片。
- 运行态复测已经通过；仍需补跑单元测试作为提交前验证。

当前阶段不做：

- 复杂登录鉴权。
- 多租户知识库隔离。
- Chroma / Milvus / Redis。
- Rerank、Query Rewrite、Intent Classifier。
- 大规模 UI 重构。
- 图片 OCR。

## 5. 第三阶段：最小角色管理

启动条件：知识库闭环补测通过。

最小目标：

- `ADMIN`：登录后可上传、删除和管理知识库。
- `USER`：登录后只能使用问答。

推荐实现路径：

1. 完善 `user` 表、`UserMapper` 和用户实体。
2. 密码使用 BCrypt，禁止明文和 MD5。
3. 实现 `AuthController.login/register`。
4. 前端 `Login.vue` 改为真实登录。
5. 前端保存登录态并统一携带 Token。
6. 后端文档管理接口限制为 `ADMIN`。
7. 普通 `USER` 只能访问问答能力。

本阶段不做：

- 复杂权限菜单。
- 用户私有知识库。
- 组织、学院、班级等复杂身份体系。
- OAuth、单点登录等外部认证。

## 6. 第四阶段：向量持久化

启动条件：最小角色管理稳定，且内存向量库的重启重建成本开始影响开发或演示。

推荐方案：Chroma。

原因：

- 本项目规模中小，Chroma 成本低。
- LangChain4j 有现成 `ChromaEmbeddingStore`。
- 理论上优先替换 `EmbeddingStoreConfig` 中的 Bean。
- Chroma 上线后，`KnowledgeWarmupService` 可以退役或转为迁移工具。

当前不提前接入 Chroma，避免在知识库闭环和角色管理尚未稳定前扩大基础设施复杂度。

## 7. 第五阶段：RAG 质量增强

启动条件：基础业务稳定，且出现明确的回答质量问题。

可选能力：

- 多轮对话记忆：解决“它”“上面那个”等上下文引用。
- Query Rewrite：将口语化问题改写成独立检索问题。
- Rerank：对向量召回结果重新排序。
- Intent Classifier：区分闲聊、知识库问答、系统操作。

这些能力只有在问题真实出现时才引入，避免过度工程。

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

### 8.5 文档类型策略

- `file_type` 保存短扩展名，例如 `pdf`、`docx`、`xlsx`。
- 不把完整 MIME 写入 `file_type`。
- 如果未来确实需要保留完整 MIME，应新增独立字段。

### 8.6 RAG 黄金测试

每次阶段性提交前至少验证：

1. 命中测试
   - 上传校园资料。
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
5. 重启测试
   - 后端重启后已完成文档可重新参与检索。

## 9. 当前最近任务

1. 补跑后端单元测试，确认 `DocumentServiceImplTest` 和 `KnowledgeWarmupServiceTest` 通过。
2. 提交删除向量清理修复和路线图更新。
3. 开始最小角色管理设计和数据库表梳理。
4. 角色管理稳定后，再评估 Chroma 向量持久化。
