# Campus RAG 后端兼容入口

本文件仅保留给会默认读取 `CLAUDE.md` 的工具。后端正式架构上下文以 `ARCHITECTURE_ROADMAP.md` 为准。

开发前请先确认：

1. 读取根目录 `AGENTS.md`、`PROJECT_SUMMARY.md`、`DEV_LOG.md`。
2. 读取本仓库 `ARCHITECTURE_ROADMAP.md`。
3. 执行 `git status --short --branch`，确认当前后端改动范围。

当前后端状态：

- RAG MVP 主链路已完成。
- PDF / Word / Excel / PPT 文档摄入已完成。
- `InMemoryEmbeddingStore + KnowledgeWarmupService` 启动预热已完成。
- 删除文档时同步清理内存向量切片已完成并推送。
- 当前阶段进入最小角色管理：`ADMIN` 管理知识库，`USER` 使用问答。

后端约束：

- Prompt 必须外置到 `src/main/resources/prompts/`。
- `file_type` 只保存短扩展名，例如 `pdf`、`docx`、`xlsx`、`pptx`。
- 当前不要提前引入 Redis、Chroma、OCR、Rerank、Query Rewrite。
- 新增或修改数据库字段时必须同步 Entity、Mapper interface、Mapper XML 和相关 SQL。
