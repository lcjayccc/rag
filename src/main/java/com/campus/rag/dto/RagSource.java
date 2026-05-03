package com.campus.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * RAG 命中的来源切片，用于回答溯源和查询日志记录。
 */
@Data
@AllArgsConstructor
public class RagSource {

    private Long documentId;

    private String fileName;

    private Integer chunkIndex;

    private Double score;
}
