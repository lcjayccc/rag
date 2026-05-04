package com.campus.rag.search;

import dev.langchain4j.data.document.Metadata;

/**
 * 统一检索结果模型，承载多路检索和融合后的得分。
 */
public class SearchResult {

    private final String text;
    private final Metadata metadata;
    private Double vectorScore;
    private Double bm25Score;
    private Double rrfScore;
    private Double rerankScore;

    public SearchResult(String text, Metadata metadata) {
        this.text = text;
        this.metadata = metadata;
    }

    public String getText() { return text; }
    public Metadata getMetadata() { return metadata; }

    public Double getVectorScore() { return vectorScore; }
    public void setVectorScore(Double vectorScore) { this.vectorScore = vectorScore; }

    public Double getBm25Score() { return bm25Score; }
    public void setBm25Score(Double bm25Score) { this.bm25Score = bm25Score; }

    public Double getRrfScore() { return rrfScore; }
    public void setRrfScore(Double rrfScore) { this.rrfScore = rrfScore; }

    public Double getRerankScore() { return rerankScore; }
    public void setRerankScore(Double rerankScore) { this.rerankScore = rerankScore; }

    public Double effectiveScore() {
        if (rerankScore != null) return rerankScore;
        if (rrfScore != null) return rrfScore;
        if (vectorScore != null) return vectorScore;
        return bm25Score;
    }
}
