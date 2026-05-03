package com.campus.rag.dto;

import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 检索后的结构化结果。
 *
 * <p>除了最终喂给大模型的 Prompt，也保留召回数、分数和来源，方便后续写入
 * rag_query_log，并在回答末尾展示参考来源。
 */
@Data
public class RagPromptResult {

    private String prompt;

    private int retrievedCount;

    private Double topScore;

    private boolean ragHit;

    private boolean rejected;

    private double minScoreUsed;

    private long retrievalLatencyMs;

    private List<RagSource> sources = List.of();

    private String citationIndex;

    public String sourceDocumentIdsJson() {
        String ids = sources.stream()
                .map(RagSource::getDocumentId)
                .filter(id -> id != null)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return "[" + ids + "]";
    }

    public String sourceNamesText() {
        return sources.stream()
                .map(RagSource::getFileName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining("、"));
    }

    public String citationMarkdown() {
        String idx = getCitationIndex();
        if (idx == null || idx.isBlank()) {
            return "";
        }
        return "\n\n> 参考文献：\n" + idx;
    }
}
