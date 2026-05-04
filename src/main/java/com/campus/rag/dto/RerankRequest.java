package com.campus.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        @JsonProperty("top_n")
        private Integer topN;

        @JsonProperty("return_documents")
        private Boolean returnDocuments;
    }
}
