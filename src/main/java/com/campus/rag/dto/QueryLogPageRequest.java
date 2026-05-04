package com.campus.rag.dto;

import lombok.Data;

@Data
public class QueryLogPageRequest {
    private int page = 1;
    private int pageSize = 20;
    private String keyword;
    private Boolean ragHit;
    private Long userId;
}
