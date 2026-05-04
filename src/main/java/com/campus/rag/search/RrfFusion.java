package com.campus.rag.search;

import java.util.*;

/**
 * Reciprocal Rank Fusion (RRF) 融合算法。
 *
 * <p>将多路检索结果按排名融合，公式：score(d) = sum(1.0 / (k + rank_i(d)))
 * k 值越大，排名靠后的文档影响力越小。默认 k=60。
 */
public class RrfFusion {

    private final int k;

    public RrfFusion(int k) {
        this.k = k;
    }

    public RrfFusion() {
        this(60);
    }

    /**
     * 融合向量检索和 BM25 检索结果。
     *
     * @param vectorResults 向量检索结果（已按分数降序）
     * @param bm25Results   BM25 检索结果（已按分数降序）
     * @param topK          最终返回数量
     * @return RRF 融合后的结果
     */
    public List<SearchResult> fuse(List<SearchResult> vectorResults,
                                   List<SearchResult> bm25Results,
                                   int topK) {
        // key: documentId, value: accumulated RRF score
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, SearchResult> resultMap = new LinkedHashMap<>();

        // 向量检索排名
        accumulate(vectorResults, rrfScores, resultMap);

        // BM25 检索排名
        accumulate(bm25Results, rrfScores, resultMap);

        // 按 RRF 分数降序排列
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    SearchResult result = resultMap.get(e.getKey());
                    SearchResult fused = new SearchResult(result.getText(), result.getMetadata());
                    fused.setVectorScore(result.getVectorScore());
                    fused.setBm25Score(result.getBm25Score());
                    fused.setRrfScore(e.getValue());
                    return fused;
                })
                .toList();
    }

    private void accumulate(List<SearchResult> results,
                            Map<String, Double> rrfScores,
                            Map<String, SearchResult> resultMap) {
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            String docId = result.getMetadata().getString("documentId");
            if (docId == null) continue;

            double rrfContrib = 1.0 / (k + i + 1); // i+1 转为 1-based 排名
            rrfScores.merge(docId, rrfContrib, Double::sum);

            SearchResult existing = resultMap.get(docId);
            if (existing == null) {
                resultMap.put(docId, result);
            } else {
                // 合并两路分数
                if (result.getVectorScore() != null) existing.setVectorScore(result.getVectorScore());
                if (result.getBm25Score() != null) existing.setBm25Score(result.getBm25Score());
            }
        }
    }
}
