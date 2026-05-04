# Campus RAG 后端兼容入口

本文件是后端目录事实源，供 Codex 默认读取，也供 Claude 通过 `CLAUDE.md` 跳转读取。`CLAUDE.md` 只保留自动入口，不维护独立事实。

开发前请先确认：

1. 读取根目录 `AGENTS.md`、`PROJECT_SUMMARY.md`、`DEV_LOG.md`。
2. 读取本仓库 `ARCHITECTURE_ROADMAP.md`。
3. 执行 `git status --short --branch`，确认当前后端改动范围。

当前后端状态：

- RAG MVP 主链路已完成。
- PDF / Word / Excel / PPT 文档摄入已完成。
- `InMemoryEmbeddingStore + KnowledgeWarmupService` 启动预热已完成。
- 删除文档时同步清理内存向量切片已完成并推送。
- 最小角色管理已完成：`ADMIN` 管理知识库，`USER` 使用问答。
- `rag_query_log` 查询日志、引用溯源、分类知识库已完成。
- RAG 检索质量优化已完成第一轮：minScore 校准为 0.65、Chunk 行内角标溯源、基于 Session 的 Query Rewrite。
- Gradle 命令行测试环境已恢复：`clean testClasses` 通过，历史 4 个类加载失败测试通过，全量后端测试 22/22 通过。

Gradle / IDEA 推荐配置：

- 后端路径：`D:\Graduation-projects\campus-rag-system\rag-backend`
- Gradle JVM：`E:\JAVAJDK\jdk-21`
- `GRADLE_USER_HOME`：`E:\Learn\javaEE\Gradle-8.9\caches`
- Wrapper 镜像：`https://mirrors.cloud.tencent.com/gradle/gradle-9.3.0-bin.zip`

后端约束：

- Prompt 必须外置到 `src/main/resources/prompts/`。
- `file_type` 只保存短扩展名，例如 `pdf`、`docx`、`xlsx`、`pptx`。
- 当前阶段引入 Chroma（向量持久化）、Redis（会话/限流）、Rerank（DashScope gte-rerank）、Intent Classifier（轻量三分类）；每 Phase 独立可验收可回滚。Query Rewrite 已做最小可验证版本，Phase 3 增强为问题拆分。
- 不做：OCR（图片扫描件）、Kafka/RocketMQ、分布式锁、复杂权限、MCP 工具集成。
- 新增或修改数据库字段时必须同步 Entity、Mapper interface、Mapper XML 和相关 SQL。

## 后端 Codex Agent 启动 Prompt

每次在后端目录启动 Codex 时，优先使用以下 Prompt。未拿到 Claude Code 父目录主控任务卡前，不要直接修改代码。

```text
你只负责 rag-backend 后端仓库。

先读取：
AGENTS.md
ARCHITECTURE_ROADMAP.md
build.gradle
gradle.properties
gradle/wrapper/gradle-wrapper.properties

先执行：
git status --short --branch

不要提交、不要推送。
不要修改前端和父目录文档。
不要回滚、覆盖或整理其他 Agent 的未提交改动。

本轮任务：
【粘贴 Claude Code 父目录主控分配给后端的任务】

允许修改文件：
【明确列出】

禁止修改文件：
【明确列出】

后端约束：
- Prompt 必须外置到 src/main/resources/prompts/
- file_type 只保存短扩展名，例如 pdf/docx/xlsx/pptx
- 修改数据库字段必须同步 Entity、Mapper interface、Mapper XML 和 SQL
- 当前阶段引入 Chroma、Redis、Rerank、Intent Classifier；每 Phase 独立可验收可回滚。不做 OCR、Kafka/RocketMQ、分布式锁、复杂权限、MCP 工具集成

验证命令优先使用：
$env:GRADLE_USER_HOME='E:\Learn\javaEE\Gradle-8.9\caches'
$env:JAVA_HOME='E:\JAVAJDK\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon --max-workers=1 clean testClasses --console=plain --stacktrace
.\gradlew.bat --no-daemon --max-workers=1 test --console=plain --stacktrace

完成后输出：
1. 修改了哪些文件
2. 为什么改
3. 如何验证
4. 剩余风险
5. 是否建议提交
```
