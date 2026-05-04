package com.campus.rag.dto;

import lombok.Data;
import java.util.List;

@Data
public class RerankResponse {
    private List<RerankResult> output;

    @Data
    public static class RerankResult {
        private Integer index;
        private Double relevanceScore;
    }
}
