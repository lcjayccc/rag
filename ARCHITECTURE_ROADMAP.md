# Campus RAG 架构路线图

更新时间：2026-05-03

## 1. 项目定位

Campus RAG 是一个面向河南工业大学校园资料的智能问答系统。系统目标不是通用聊天，而是围绕学校制度、通知、表格、办事指南等资料，提供可检索、低幻觉、可演示、可测试的问答能力。

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
| 删除清理 | 按 `documentId` 移除内存向量切片 | 已完成 |
| Prompt | `src/main/resources/prompts/` | 已统一外置 |
| 登录鉴权 | 真实登录 + `ADMIN/USER` | 已完成 |
| 查询日志 | `rag_query_log` | 基础写入已完成 |
| 引用溯源 | 来源文档标注 | 已完成基础展示 |
| 分类知识库 | `document_category` + 范围检索 | 前后端代码已接入，待联调验证 |
| 向量持久化 | Chroma | 后续评估 |
| OCR | 图片/扫描件文本识别 | 后续独立阶段 |

## 3. 第一阶段：RAG MVP 主链路

状态：已完成。

已完成内容：

- `DocumentServiceImpl`
  - 文档上传、文件落盘、数据库状态维护。
  - 支持 PDF、Word、Excel、PPT。
  - 保存短扩展名到 `file_type`，避免 Office MIME 超长导致 MySQL 截断。
  - 删除数据库记录后清理本地文件。
- `DocumentIndexingService`
  - 根据扩展名选择 PDFBox 或 POI 解析器。
  - 完成解析、切片、向量化、写入 `EmbeddingStore`。
  - 支持按 `documentId` 删除内存向量切片。
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

状态：已完成。

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
  - 删除数据库记录、本地文件和内存向量切片。
  - 文档列表按更新时间倒序。
- 聊天页回归：
  - RAG 问答未被控制台改动破坏。
  - Markdown 展示保持安全、整洁、可读。

已验证结果：

- `/dashboard` 可通过前端代理拉取 `GET /api/documents`。
- 当前知识库返回 7 条业务文档。
- 问题“校级三好学生审批表的格式是什么”能命中文档内容，并正常收到 `[DONE]`。
- PDF、DOCX、XLSX、PPTX 上传接口已完成补测。
- 无效 PPTX 会进入失败状态。
- 删除接口已验证可删除数据库记录并清理本地文件。
- 删除向量残留修复已完成运行态验证：删除 DOCX/PPTX 后立即提问对应 token，不再命中。
- 重启预热验证通过：历史 PDF 和名单文档在 Spring Boot 重启后仍可命中。
- 浏览器 UI 搜索和删除确认弹窗已验证通过。
- 真实中文 PDF 抽取和 RAG 命中验证通过。

## 5. 第三阶段：最小角色管理

状态：已完成。

目标：把演示用户改造为真实登录用户，并形成最小但清晰的毕业设计权限边界。

角色边界：

- `ADMIN`：登录后可上传、删除、查看和管理知识库。
- `USER`：登录后只能使用智能问答。

已完成范围：

1. `user` 表、`User.java`、`UserMapper`、`UserMapper.xml` 已同步角色、状态和最近登录时间。
2. 密码使用 BCrypt，并兼容旧明文密码首次登录后自动升级。
3. 已实现 `AuthController.login/register`。
4. 已增加 HMAC Token 生成、解析和请求过滤。
5. 文档管理接口已限制为 `ADMIN`。
6. 问答接口要求登录用户访问。
7. 前端已完成真实登录、本地登录态、请求认证、路由守卫、角色导航和退出登录。
8. 已保留必要测试：登录成功、密码错误、普通用户禁止删除文档、管理员可管理文档。

本阶段不做：

- 复杂权限菜单。
- 用户私有知识库。
- 组织、学院、班级等复杂身份体系。
- OAuth、单点登录等外部认证。
- Redis 黑名单。

## 6. 第四阶段：论文可证明性能力

状态：基础能力已完成。

启动条件：最小角色管理已完成。

优先级高于 Chroma 和 Redis。

目标：让系统能为论文测试章节提供可量化数据，而不是只靠截图和人工描述。

推荐能力：

- `rag_query_log`
  - 记录问题、用户、召回数量、最高相似度、是否命中、是否拒答、耗时。
  - 支撑命中率、拒答率、平均延迟等指标。
- 引用溯源
  - 回答末尾展示参考文档。
  - 后端保留命中文档 ID 或文档名，前端能展示来源。
- RAG 黄金测试集
  - A 类：知识库内有明确答案。
  - B 类：知识库外问题，预期拒答。
  - C 类：口语化或模糊问题，观察语义召回能力。

已完成范围：

1. 新增 `rag_query_log` 表、实体、Mapper 和写入服务。
2. `RagService` 已从单纯 Prompt 升级为结构化检索结果，保留召回数量、最高分和来源文档。
3. SSE 回答完成前会追加参考来源，并保持 `[DONE]` 完成帧。
4. 查询日志写入不影响主问答链路，写入失败只记录警告。
5. 已补充迁移 SQL 和基础单元测试。

## 7. 第五阶段：分类知识库

状态：已完成（分类检索隔离联调验证通过）。

启动条件：角色管理、查询日志和引用溯源稳定。

目标：让知识库从”全局资料池”升级为”按校园业务范围检索”。

已完成范围：

### 后端

1. 新增 `document_category` 建表脚本（`docs/sql/2026-05-03-document-category.sql`）和默认分类初始化数据。
2. 新增分类实体 `DocumentCategory.java`、Mapper `DocumentCategoryMapper.java`、Service `DocumentCategoryService.java` 和管理接口 `DocumentCategoryController.java`。
3. 文档上传接口支持可选 `categoryId`，`document` 表新增 `category_id` 列。
4. `DocumentIndexingServiceImpl` 在切片元数据中写入 `categoryId`。
5. `RagServiceImpl` 支持可选 `categoryId`，通过 `MetadataFilter` 限制向量召回范围。
6. `ChatController` 的 SSE 接口已支持可选 `categoryId` 参数，兼容全库和分类检索两种问答范围。

### 前端

1. `Dashboard.vue` 可加载分类列表，上传前选择资料分类。
2. 文档列表根据 `categoryId` 展示分类名称，并支持按分类搜索。
3. `Chat.vue` 可选择”全库”或具体分类，并将 `categoryId` 传给 SSE 问答接口。

### 验证结果

- 分类知识库端到端联调通过：控制台上传文档到指定分类后，聊天页按不同分类范围检索可实现知识隔离与精准召回。

## 8. 第六阶段：RAG 检索质量深度优化

状态：下一阶段最高优先级。

启动条件：分类知识库已完成并稳定。

目标：降低垃圾召回、改善多轮追问、提升引用可解释性，让系统从"能答"升级为"答得准、可追溯"。

### 8.1 动态上调检索阈值（minScore）

- 当前问题：`minScore=0.6` 阈值偏低，低质量碎片可能混入上下文，导致大模型回答偏离知识库事实。
- 优化策略：将 `RagService` 的 `minScore` 从 `0.6` 上调至 `0.75`。
- 原则：宁可触发兜底拒答（"暂无相关信息"），也不让低质量碎片污染 LLM 上下文。
- 实施位置：`RagServiceImpl.java` 中 `minScore` 常量或可配置参数。

### 8.2 Chunk 级精准溯源

- 当前问题：引用溯源仅在回答末尾拼接来源文档名，无法定位到具体段落或句子，论文可解释性论证支撑不足。
- 优化策略：在 Prompt 的 Context 组装阶段，为每条召回切片注入段落来源标签（如 `[来源：文档名-段落N]`），要求 LLM 在生成回答时携带论文角标样式的行内引用（如 `[1]`、`[2]`）。
- 最终效果：用户可逐句追溯到具体文档的具体段落。
- 实施位置：`prompts/answer-chat-kb.st`（Context 组装格式）和 `RagServiceImpl.java`（来源元数据注入）。

### 8.3 基于 Session 的 Query Rewrite

- 当前问题：单轮向量检索无法理解"这两个"、"为什么"等依赖上文的代词和省略，导致多轮追问场景召回率下降。
- 优化策略：引入 `session_id`，在多轮对话中保留历史问答；在向量化检索前，使用轻量小模型将用户当前问题与历史上下文合并，重写为完整独立问题。
- 原则：最小可验证版本优先，不引入完整对话管理系统；Query Rewrite 失败时回退到原始问题，不影响主链路。
- 实施位置：`RagServiceImpl.java`（检索前增加 Rewrite 环节）、新增 Rewrite Prompt 模板。

## 9. 后续基础设施评估

### Chroma 向量持久化

启动条件：内存向量库重启预热成本开始影响演示，或分类知识库稳定后需要更接近生产形态。

推荐原则：

- 优先替换 `EmbeddingStoreConfig` 中的 `EmbeddingStore` Bean。
- 避免业务代码依赖具体向量库实现类。
- `KnowledgeWarmupService` 可保留为迁移或重建工具。

### Redis

启动条件：需要 Token 黑名单、多轮对话上下文或配置缓存。

当前不提前接入，避免角色管理阶段扩大基础设施复杂度。

### OCR

启动条件：确实需要支持图片、扫描 PDF 或截图类校园资料。

OCR 应作为独立链路设计，不混入普通 PDF / Office 解析逻辑。

## 9. 架构约束

### 9.1 防过度设计

当前阶段禁止引入：

- RocketMQ、Kafka、Zookeeper
- 分布式锁
- Redis 会话体系
- Chroma / Milvus
- Rerank、Query Rewrite、Intent Classifier
- 复杂权限框架

除非当前阶段有明确验收需求，否则不新增基础设施。

### 9.2 Prompt 外置

所有 System Prompt 必须外置到：

```text
src/main/resources/prompts/
```

允许格式：

- `.st`
- `.txt`

禁止在 Service 或 Controller 中硬编码大段 Prompt。

### 9.3 面向接口编程

业务代码注入 LangChain4j 组件时使用接口类型：

- `EmbeddingStore<TextSegment>`
- `EmbeddingModel`

具体实现类只允许出现在配置类中，例如 `EmbeddingStoreConfig`。

### 9.4 MyBatis XML 同步

新增或修改数据库字段时，必须同步：

- Entity
- Mapper interface
- Mapper XML `resultMap`
- INSERT / UPDATE / SELECT SQL

### 9.5 文档类型策略

- `file_type` 保存短扩展名，例如 `pdf`、`docx`、`xlsx`、`pptx`。
- 不把完整 MIME 写入 `file_type`。
- 如果未来确实需要保留完整 MIME，应新增独立字段。

### 9.6 RAG 黄金测试

每次阶段性提交前至少验证：

1. 命中测试：提问文档中存在的问题，预期回答引用知识库内容。
2. 边界测试：提问知识库外问题，预期明确说明知识库暂无信息，不编造。
3. 日期测试：提问当前年份，预期回答 2026 年。
4. 流式测试：前端能收到完整回答和 `[DONE]`。
5. 重启测试：后端重启后已完成文档可重新参与检索。

## 10. 当前最近任务

1. 在 MySQL 应用 `document_category` 和 `document.category_id` 迁移 SQL。
2. 启动前后端，验证分类上传、分类展示和分类范围问答。
3. 验证同一问题在全库和具体分类下的召回结果差异。
