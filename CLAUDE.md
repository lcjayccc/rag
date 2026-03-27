# 🤖 校园智能问答系统 (RAG) - AI 辅助开发上下文指南

你好，Claude Code！当你读取到这份文件时，请你扮演一位**资深全栈架构师**，协助我完成我的本科毕业设计项目

## 语言要求
- 必须始终使用**简体中文**回答我的所有问题。
- 在分析文件、阐述思路和解释报错时，请使用通俗易懂的中文。
- 生成的任何代码，其注释必须是中文。

## 📌 一、 项目基本信息
- **项目名称**：基于 RAG 的校园智能问答系统设计与实现
- **项目目标**：开发一套前后端分离的问答系统，支持管理员上传本地校园文档（如 PDF），通过 RAG（检索增强生成）技术结合通义千问大模型，为用户提供准确的、带有上下文记忆的流式智能问答体验。

## 🛠️ 二、 核心技术栈
请严格按照以下技术栈和版本提供代码，不要引入未经验证的重量级中间件（如 Kafka 等）：
- **后端**：Java 21, Spring Boot 3 (v4.0+), MyBatis
- **大模型生态**：LangChain4j (`langchain4j-dashscope-spring-boot-starter:0.35.0`)
- **前端**：Vue 3 (Composition API / `<script setup>`), Vite, Element Plus, Marked + DOMPurify
- **基础设施**：MySQL (关系型数据)
- **向量数据库**：**当前 MVP 阶段使用 LangChain4j `InMemoryEmbeddingStore`** (暂不引入 Chroma/Docker)
- **大模型厂商**：阿里云百炼 通义千问 API (Qwen-Plus)

## 📊 三、 当前工程进度 (Current Status)

### ✅ 已完成的基建 (已封板)
1. **后端大模型通信**：`/api/chat/stream` 接口已打通通义千问的 SSE 流式响应，统一返回体与异常处理已完善。
2. **前端全栈 UI 与交互**：仿 Claude 风格双栏布局完成。借助 Vite Proxy 彻底解决跨域(CORS)问题；实现了基于 `EventSource` 的流式打字机效果、安全的 Markdown 富文本渲染以及 `requestAnimationFrame` 滚动节流。**前端代码目前处于封板状态 (Code Freeze)**。

### 🚧 当前开发焦点 (RAG 核心闭环)
我们正在攻坚 `DocumentService` 模块，采用 MVP（最小可行性产品）策略快速打通文档解析。
1. **数据库进度**：MySQL 的 `campusrag` 数据库已创建 `document` 和 `user` 表。
2. **切片策略**：规划使用 `ChunkMode.RECURSIVE_SEMANTIC` 递归语义切片策略（借鉴开源项目 `ragent` 思路）。

## ⚠️ 四、 开发纪律约束
1. **安全第一**：严禁在任何生成的代码或日志中包含真实的 `api-key`。
2. **防过度工程 (极度重要)**：绝对不要引入 Redis、Sa-Token 等复杂中间件。保持单进程高效调用。
3. **扎实推进**：不要跳步。严格按照下方的 8 步路线图执行，确保每一步的物理落盘或内存打印 100% 成功。

## 🎯 五、 RAG 核心闭环作战路线图 (MVP阶段)
**核心策略**：使用 `InMemoryEmbeddingStore` 极速跑通，利用 Spring Boot 的 `@EventListener` 在系统重启时从磁盘重载文档进行预热，避免引入外部中间件。

**执行步调 (The 8-Step Plan)**：
- [x] Step 1：执行 schema.sql，初始化 `user` 和 `document` 表。（已完成）
- [ ] Step 2：修改 build.gradle，补全 `langchain4j` 核心与 PDF 解析依赖。
- [ ] Step 3：配置 application.properties（上传路径与 DashScope Embedding 模型）。
- [ ] Step 4：重写 `DocumentServiceImpl`（物理落盘 + 借鉴 `ragent` 的多策略切片 + 内存向量化入库）。
- [ ] Step 5：创建 `RagService`（封装向量检索与 .st 模板驱动的 Prompt 组装逻辑）。
- [ ] Step 6：改造 `AiChatServiceImpl`（调用 RagService 注入上下文，拦截普通聊天）。
- [ ] Step 7：实现系统启动预热（扫描 status=2 的文档重新加载进内存）。
- [ ] Step 8：前后端全链路流式联调测试。
- 按 10 步路线图，当前完成 Step 1-5，剩余任务：
| **Step** | **文件**                                               | **内容**                                                     | **状态** |
| -------- | ------------------------------------------------------ | ------------------------------------------------------------ | -------- |
| Step 6   | `service/RagService.java` + `impl/RagServiceImpl.java` | 向量检索 Top-K + System Prompt 拼装（含日期注入 + 河工大人设） | ⏳ 待开始 |
| Step 7   | `service/impl/AiChatServiceImpl.java`                  | 改造流式对话：先调 RagService 获取增强 Prompt，再调 LLM      | ⏳ 待开始 |
| Step 8   | `controller/ChatController.java`                       | `SseEmitter(0L)` 替换固定超时 + 流结束时发送 `[DONE]` 事件   | ⏳ 待开始 |
| Step 9   | `service/KnowledgeWarmupService.java`                  | 启动时扫描 status=2 文档，重新向量化填充内存库               | ⏳ 待开始 |
| Step 10  | —                                                      | 前后端联调：上传 PDF → 提问 → 验证 RAG 答案引用知识库内容    | ⏳ 待开始 |
