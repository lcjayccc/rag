package com.campus.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatOverviewResponse {

    private DocumentStats documents;
    private QueryStats queries;
    private SystemStatus system;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentStats {
        private int total;
        private int completed;
        private int processing;
        private int failed;
        private Map<String, Integer> byCategory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryStats {
        private int total;
        private int today;
        private double hitRate;
        private int avgLatencyMs;
        private int rejectedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemStatus {
        private String vectorStore;
        private boolean hybridEnabled;
        private double minScore;
        private int topK;
    }
}
