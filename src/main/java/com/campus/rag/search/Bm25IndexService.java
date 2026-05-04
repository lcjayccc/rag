package com.campus.rag.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 BM25 倒排索引，支持增量更新。
 *
 * <p>文档集较小时（<1000 篇校园文档），内存索引足够快。
 * BM25 公式：score(D,Q) = sum(IDF(qi) * TF(qi,D) * (k1+1) / (TF(qi,D) + k1*(1-b+b*|D|/avgdl)))
 */
@Slf4j
@Component
public class Bm25IndexService {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final Bm25Tokenizer tokenizer = new Bm25Tokenizer();

    // docId → (token → frequency)
    private final Map<String, Map<String, Integer>> docTermFreq = new ConcurrentHashMap<>();
    // docId → document length (term count)
    private final Map<String, Integer> docLengths = new ConcurrentHashMap<>();
    // token → document frequency
    private final Map<String, Integer> docFreq = new ConcurrentHashMap<>();
    // docId → SearchResult cached for retrieval
    private final Map<String, SearchResult> docCache = new ConcurrentHashMap<>();

    private int totalDocs = 0;
    private double avgDocLength = 0.0;

    public synchronized void addDocument(Long docId, SearchResult result) {
        String chunkIdx = result.getMetadata().getString("chunkIndex");
        String key = docId + "_" + (chunkIdx != null ? chunkIdx : "0");

        List<String> tokens = tokenizer.tokenize(result.getText());
        Map<String, Integer> tf = new HashMap<>();
        for (String token : tokens) {
            tf.merge(token, 1, Integer::sum);
        }

        docTermFreq.put(key, tf);
        docLengths.put(key, tokens.size());
        docCache.put(key, result);
        totalDocs++;

        for (String token : tf.keySet()) {
            docFreq.merge(token, 1, Integer::sum);
        }

        recalcAvgDocLength();
        log.debug("[BM25] 已添加 key={}, tokens={}", key, tokens.size());
    }

    public synchronized void removeDocument(Long docId) {
        String prefix = docId + "_";
        List<String> keys = docTermFreq.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .toList();

        for (String key : keys) {
            Map<String, Integer> tf = docTermFreq.remove(key);
            docLengths.remove(key);
            docCache.remove(key);

            if (tf != null) {
                totalDocs--;
                for (String token : tf.keySet()) {
                    Integer df = docFreq.get(token);
                    if (df != null) {
                        if (df <= 1) {
                            docFreq.remove(token);
                        } else {
                            docFreq.put(token, df - 1);
                        }
                    }
                }
            }
        }
        if (!keys.isEmpty()) {
            recalcAvgDocLength();
            log.debug("[BM25] 已移除文档 docId={}, chunks={}", docId, keys.size());
        }
    }

    public synchronized void buildIndex(List<SearchResult> documents) {
        clear();
        for (SearchResult doc : documents) {
            String docId = doc.getMetadata().getString("documentId");
            if (docId == null) continue;
            addDocument(Long.valueOf(docId), doc);
        }
        log.info("[BM25] 全量索引构建完成，共 {} 条文档", totalDocs);
    }

    public synchronized void clear() {
        docTermFreq.clear();
        docLengths.clear();
        docFreq.clear();
        docCache.clear();
        totalDocs = 0;
        avgDocLength = 0.0;
    }

    /**
     * BM25 检索，返回 topK 个结果。
     */
    public List<SearchResult> search(String query, int topK) {
        if (totalDocs == 0 || query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        List<String> queryTokens = tokenizer.tokenize(query);
        if (queryTokens.isEmpty()) {
            return Collections.emptyList();
        }

        // 计算每个文档的 BM25 分数
        Map<String, Double> scores = new HashMap<>();
        for (String token : queryTokens) {
            int df = docFreq.getOrDefault(token, 0);
            if (df == 0) continue;
            double idf = Math.log(1.0 + (totalDocs - df + 0.5) / (df + 0.5));

            for (Map.Entry<String, Map<String, Integer>> entry : docTermFreq.entrySet()) {
                String docId = entry.getKey();
                int tf = entry.getValue().getOrDefault(token, 0);
                if (tf == 0) continue;

                int dl = docLengths.getOrDefault(docId, 0);
                double norm = 1.0 - B + B * dl / avgDocLength;
                double score = idf * (tf * (K1 + 1)) / (tf + K1 * norm);
                scores.merge(docId, score, Double::sum);
            }
        }

        // 排序取 Top-K
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    SearchResult result = docCache.get(e.getKey());
                    if (result != null) {
                        SearchResult copy = new SearchResult(result.getText(), result.getMetadata());
                        copy.setBm25Score(e.getValue());
                        return copy;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private void recalcAvgDocLength() {
        if (docLengths.isEmpty()) {
            avgDocLength = 0.0;
        } else {
            avgDocLength = docLengths.values().stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0.0);
        }
    }

    int getTotalDocs() { return totalDocs; }
}
