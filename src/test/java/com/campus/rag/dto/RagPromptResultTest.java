package com.campus.rag.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagPromptResultTest {

    @Test
    void citationMarkdownDeduplicatesSourceNames() {
        RagPromptResult result = new RagPromptResult();
        result.setSources(List.of(
                new RagSource(1L, "校级三好学生.pdf", 0, 0.92),
                new RagSource(1L, "校级三好学生.pdf", 1, 0.88),
                new RagSource(2L, "奖学金名单.docx", 0, 0.81)
        ));

        assertEquals("\n\n> 参考来源：校级三好学生.pdf、奖学金名单.docx", result.citationMarkdown());
        assertEquals("[1,2]", result.sourceDocumentIdsJson());
    }
}
