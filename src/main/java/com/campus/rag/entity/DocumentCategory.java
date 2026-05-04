package com.campus.rag.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档分类。
 *
 * <p>用于把全局资料池逐步升级为按校园业务范围管理和检索的知识库。
 */
@Data
public class DocumentCategory {

    private Long id;

    private String name;

    private String code;

    private String description;

    private Integer sortOrder;

    private Boolean enabled;

    /** 默认切片策略：FIXED_SIZE / STRUCTURE_AWARE / SEMANTIC，默认 FIXED_SIZE */
    private String defaultChunkingStrategy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
