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
| 登录鉴权 | 真实登录 + `ADMIN/USER` | 当前阶段 |
| 查询日志 | `rag_query_log` | 角色管理后优先 |
| 引用溯源 | 来源文档标注 | 角色管理后优先 |
| 分类知识库 | `document_category` + 范围检索 | 查询日志后 |
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

状态：当前阶段。

目标：把演示用户改造为真实登录用户，并形成最小但清晰的毕业设计权限边界。

角色边界：

- `ADMIN`：登录后可上传、删除、查看和管理知识库。
- `USER`：登录后只能使用智能问答。

后端推荐实现路径：

1. 完善 `user` 表，新增角色、状态和最近登录时间等必要字段。
2. 同步 `User.java`、`UserMapper`、`UserMapper.xml`。
3. 密码使用 BCrypt，禁止明文和 MD5。
4. 实现 `AuthController.login/register`。
5. 增加 Token 生成、解析和请求过滤。
6. 文档管理接口限制为 `ADMIN`。
7. 问答接口允许登录用户访问。
8. 保留必要的测试：登录成功、密码错误、普通用户禁止删除文档、管理员可管理文档。

前端推荐实现路径：

1. `Login.vue` 改为真实登录表单。
2. 登录成功后保存 token、role、username。
3. 请求层统一追加认证信息。
4. 路由守卫拦截未登录访问。
5. `MainLayout` 根据角色展示导航：
   - `ADMIN` 显示知识库控制台。
   - `USER` 只显示智能问答。
6. 退出登录清理本地登录态。

本阶段不做：

- 复杂权限菜单。
- 用户私有知识库。
- 组织、学院、班级等复杂身份体系。
- OAuth、单点登录等外部认证。
- Redis 黑名单。

## 6. 第四阶段：论文可证明性能力

启动条件：最小角色管理稳定。

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

## 7. 第五阶段：分类知识库

启动条件：角色管理、查询日志和引用溯源稳定。

目标：让知识库从“全局资料池”升级为“按校园业务范围检索”。

推荐能力：

- 新增 `document_category` 表。
- 上传文档时选择分类，如教务处、学生工作处、校级规章制度、招就处、其他。
- 文档切片元数据写入 `categoryId`。
- 问答时支持选择“全库”或某个分类范围。
- `RagService` 根据分类做 MetadataFilter。

## 8. 后续基础设施评估

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

1. 梳理当前 `user` 表和 `UserMapper`，确定最小字段迁移。
2. 设计登录注册接口返回结构和 Token 策略。
3. 实现后端登录、注册、Token 校验和 `ADMIN/USER` 角色判断。
4. 改造前端登录页、请求认证、路由守卫和导航展示。
5. 验证管理员文档管理、普通用户问答、普通用户禁止管理文档三条主路径。
