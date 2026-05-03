package com.campus.rag.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档实体
 */
@Data
public class Document {

    private Long id;
    private Long userId;
    private Long categoryId;
    private String fileName;
    private String filePath;
    private String fileType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
