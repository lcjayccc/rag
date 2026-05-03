package com.campus.rag.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 查询日志。
 *
 * <p>用于记录每次问答的检索效果，为论文中的命中率、拒答率和响应耗时分析提供数据。
 */
@Data
public class RagQueryLog {

    private Long id;

    private Long userId;

    private String question;

    private Integer retrievedCount;

    private Double topScore;

    private Boolean ragHit;

    private Boolean rejected;

    private String docIdsHit;

    private Double minScoreUsed;

    private Integer latencyMs;

    private LocalDateTime createTime;
}
