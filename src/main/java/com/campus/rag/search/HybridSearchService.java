package com.campus.rag.search;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 混合检索编排器：向量检索 + BM25 关键词检索 → RRF 融合 → Rerank 重排序。
 *
 * <p>保留 categoryId 过滤能力，支持通过配置开关控制是否启用混合检索和 Rerank。
 */
@Slf4j
@Service
public class HybridSearchService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final Bm25IndexService bm25IndexService;
    private final RerankService rerankService;
    private final RrfFusion rrfFusion = new RrfFusion(60);

    private final boolean hybridEnabled;
    private final boolean rerankEnabled;

    public HybridSearchService(EmbeddingStore<TextSegment> embeddingStore,
                               EmbeddingModel embeddingModel,
                               Bm25IndexService bm25IndexService,
                               RerankService rerankService,
                               @Value("${rag.hybrid.enabled:true}") boolean hybridEnabled,
                               @Value("${rag.rerank.enabled:true}") boolean rerankEnabled) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.bm25IndexService = bm25IndexService;
        this.rerankService = rerankService;
        this.hybridEnabled = hybridEnabled;
        this.rerankEnabled = rerankEnabled;
    }

    /**
     * 执行混合检索。
     *
     * @param question   用户问题
     * @param categoryId 分类 ID（可为 null 表示全库检索）
     * @param topK       最终返回数量
     * @param minScore   最低相似度阈值
     * @return 检索结果（按有效分数降序）
     */
    public List<SearchResult> search(String question, Long categoryId, int topK, double minScore) {
        // 1. 问题向量化
        Embedding questionEmbedding = embeddingModel.embed(question).content();

        // 2. 构建过滤条件
        Filter filter = categoryId != null
                ? metadataKey("categoryId").isEqualTo(String.valueOf(categoryId))
                : null;

        if (!hybridEnabled) {
            // 纯向量检索路径
            return vectorOnly(questionEmbedding, filter, topK, minScore);
        }

        // 3. 并行向量检索 + BM25 检索
        int fetchSize = topK * 2;
        CompletableFuture<List<SearchResult>> vectorFuture =
                CompletableFuture.supplyAsync(() -> vectorSearch(questionEmbedding, filter, fetchSize, minScore));
        CompletableFuture<List<SearchResult>> bm25Future =
                CompletableFuture.supplyAsync(() -> bm25Search(question, filter, fetchSize));

        List<SearchResult> vectorResults = vectorFuture.join();
        List<SearchResult> bm25Results = bm25Future.join();

        // 4. RRF 融合
        List<SearchResult> fused = rrfFusion.fuse(vectorResults, bm25Results, topK);

        // 5. Rerank 重排序（可选）
        if (rerankEnabled && rerankService.isEnabled()) {
            return rerankService.rerank(question, fused, topK);
        }

        return fused;
    }

    private List<SearchResult> vectorOnly(Embedding questionEmbedding, Filter filter, int topK, double minScore) {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(topK)
                .minScore(minScore)
                .filter(filter)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        return toSearchResults(result.matches());
    }

    private List<SearchResult> vectorSearch(Embedding questionEmbedding, Filter filter, int fetchSize, double minScore) {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(fetchSize)
                .minScore(minScore)
                .filter(filter)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        return toSearchResults(result.matches());
    }

    private List<SearchResult> bm25Search(String question, Filter filter, int fetchSize) {
        List<SearchResult> results = bm25IndexService.search(question, fetchSize);

        if (filter != null && filter instanceof dev.langchain4j.store.embedding.filter.comparison.IsEqualTo eq) {
            String targetCatId = eq.comparisonValue().toString();
            results = results.stream()
                    .filter(r -> targetCatId.equals(r.getMetadata().getString("categoryId")))
                    .toList();
        }

        return results;
    }

    private List<SearchResult> toSearchResults(List<EmbeddingMatch<TextSegment>> matches) {
        List<SearchResult> results = new ArrayList<>(matches.size());
        for (EmbeddingMatch<TextSegment> match : matches) {
            SearchResult sr = new SearchResult(
                    match.embedded().text(),
                    match.embedded().metadata()
            );
            sr.setVectorScore(match.score());
            results.add(sr);
        }
        return results;
    }
}
