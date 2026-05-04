package com.campus.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class RerankResponse {
    private RerankOutput output;
    private Usage usage;
    @JsonProperty("request_id")
    private String requestId;

    @Data
    public static class RerankOutput {
        private List<RerankResult> results;
    }

    @Data
    public static class RerankResult {
        private Integer index;
        @JsonProperty("relevance_score")
        private Double relevanceScore;
        private Document document;
    }

    @Data
    public static class Document {
        private String text;
    }

    @Data
    public static class Usage {
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
