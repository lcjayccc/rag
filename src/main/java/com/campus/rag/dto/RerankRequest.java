package com.campus.rag.dto;

import lombok.Data;

@Data
public class RerankRequest {
    private String model;
    private RerankInput input;
    private RerankParameters parameters;

    @Data
    public static class RerankInput {
        private String query;
        private String[] documents;
    }

    @Data
    public static class RerankParameters {
        private Integer topN;
    }
}
