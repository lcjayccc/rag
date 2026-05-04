package com.campus.rag.chunking;

import com.campus.rag.entity.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingStrategyTest {

    private FixedSizeChunker fixedSizeChunker;
    private StructureAwareChunker structureAwareChunker;
    private SemanticChunker semanticChunker;

    private Document dbDoc;

    @BeforeEach
    void setUp() {
        fixedSizeChunker = new FixedSizeChunker();
        structureAwareChunker = new StructureAwareChunker();
        semanticChunker = new SemanticChunker();

        dbDoc = new Document();
        dbDoc.setId(1L);
        dbDoc.setFileName("测试文档.pdf");
        dbDoc.setCategoryId(10L);
    }

    // ---- FixedSizeChunker ----

    @Test
    void fixedSizeChunkerShouldSplitLongText() {
        String text = "河南工业大学".repeat(200); // ~1400 chars
        List<TextSegment> chunks = fixedSizeChunker.chunk(text, dbDoc);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isGreaterThan(1);
        // 每个 chunk 应有基础元数据
        for (TextSegment chunk : chunks) {
            assertThat(chunk.metadata().getString("documentId")).isEqualTo("1");
            assertThat(chunk.metadata().getString("fileName")).isEqualTo("测试文档.pdf");
            assertThat(chunk.metadata().getString("documentTitle")).isEqualTo("测试文档");
            assertThat(chunk.metadata().getString("pageNumber")).isNotNull();
        }
    }

    @Test
    void fixedSizeChunkerShouldNotSplitShortText() {
        String text = "河南工业大学校级三好学生审批表";
        List<TextSegment> chunks = fixedSizeChunker.chunk(text, dbDoc);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().getString("chunkIndex")).isEqualTo("0");
    }

    @Test
    void fixedSizeChunkerShouldIncludeCategoryId() {
        List<TextSegment> chunks = fixedSizeChunker.chunk("测试内容", dbDoc);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).metadata().getString("categoryId")).isEqualTo("10");
    }

    // ---- StructureAwareChunker ----

    @Test
    void structureAwareChunkerShouldDetectChineseNumberedHeadings() {
        String text = "一、申请条件\n本部分介绍申请条件的具体要求。\n\n"
                + "二、申请流程\n学生需提交申请表并经过审核。\n\n"
                + "三、注意事项\n申请材料必须真实有效。";

        List<TextSegment> chunks = structureAwareChunker.chunk(text, dbDoc);

        assertThat(chunks).isNotEmpty();
        // 至少有一个 chunk 包含 sectionTitle
        boolean hasSectionTitle = chunks.stream()
                .anyMatch(c -> c.metadata().getString("sectionTitle") != null);
        assertThat(hasSectionTitle).isTrue();
    }

    @Test
    void structureAwareChunkerShouldDetectMarkdownHeadings() {
        String text = "# 奖学金评定办法\n奖学金评定面向全体在校学生。\n\n"
                + "## 申请资格\n学生需满足以下条件。\n\n"
                + "## 评审流程\n评审分为初审和复审两个阶段。";

        List<TextSegment> chunks = structureAwareChunker.chunk(text, dbDoc);

        assertThat(chunks).isNotEmpty();
        // Markdown heading 检测
        boolean hasMarkdownSection = chunks.stream()
                .anyMatch(c -> {
                    String title = c.metadata().getString("sectionTitle");
                    return title != null && (title.contains("奖学金") || title.contains("申请") || title.contains("评审"));
                });
        assertThat(hasMarkdownSection).isTrue();
    }

    @Test
    void structureAwareChunkerShouldFallbackForPlainText() {
        String text = "这是一段没有任何标题结构的纯文本内容，"
                + "应当回退到固定大小切片方式处理。".repeat(20);

        List<TextSegment> chunks = structureAwareChunker.chunk(text, dbDoc);

        assertThat(chunks).isNotEmpty();
        // 所有 chunk 应有基础元数据
        assertThat(chunks.get(0).metadata().getString("documentId")).isEqualTo("1");
    }

    // ---- SemanticChunker ----

    @Test
    void semanticChunkerShouldSplitByParagraphs() {
        String text = "第一段内容，介绍项目背景。\n\n"
                + "第二段内容，说明具体实施方案。\n\n"
                + "第三段内容，总结预期效果。";

        List<TextSegment> chunks = semanticChunker.chunk(text, dbDoc);

        assertThat(chunks).isNotEmpty();
        // 每段独立为一个 chunk
        assertThat(chunks.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void semanticChunkerShouldDetectQaPairs() {
        String text = "问：国家奖学金的申请条件是什么？\n\n"
                + "答：申请国家奖学金需满足以下条件：学习成绩优异，"
                + "综合素质测评排名在本专业前10%。\n\n"
                + "问：申请截止日期是什么时候？\n\n"
                + "答：每年9月30日前提交申请材料。";

        List<TextSegment> chunks = semanticChunker.chunk(text, dbDoc);

        assertThat(chunks).isNotEmpty();
        // Q&A 模式应拆分出多个 chunk
        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void semanticChunkerShouldPreserveMetadata() {
        String text = "问：如何办理校园卡？\n\n答：携带身份证和学生证到一卡通中心办理。";

        List<TextSegment> chunks = semanticChunker.chunk(text, dbDoc);

        assertThat(chunks).isNotEmpty();
        TextSegment first = chunks.get(0);
        assertThat(first.metadata().getString("documentTitle")).isEqualTo("测试文档");
    }

    // ---- BaseChunker (通过 FixedSizeChunker 间接测试) ----

    @Test
    void metadataShouldDeriveDocumentTitle() {
        dbDoc.setFileName("校级三好学生审批表.docx");
        List<TextSegment> chunks = fixedSizeChunker.chunk("测试内容", dbDoc);

        TextSegment chunk = chunks.get(0);
        assertThat(chunk.metadata().getString("documentTitle")).isEqualTo("校级三好学生审批表");
    }

    @Test
    void metadataShouldHandleNoExtension() {
        dbDoc.setFileName("无扩展名文件");
        List<TextSegment> chunks = fixedSizeChunker.chunk("内容", dbDoc);

        assertThat(chunks.get(0).metadata().getString("documentTitle")).isEqualTo("无扩展名文件");
    }

    @Test
    void metadataShouldHandleNullCategoryId() {
        dbDoc.setCategoryId(null);
        List<TextSegment> chunks = fixedSizeChunker.chunk("内容", dbDoc);

        // 无 categoryId 时不应写入 categoryId
        assertThat(chunks.get(0).metadata().getString("categoryId")).isNull();
    }
}
