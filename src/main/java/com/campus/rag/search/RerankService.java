package com.campus.rag.search;

import com.campus.rag.dto.RerankRequest;
import com.campus.rag.dto.RerankResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * DashScope gte-rerank 重排序服务。
 *
 * <p>对混合检索结果进行语义重排序，失败时回退不过滤任何结果。
 */
@Slf4j
@Service
public class RerankService {

    private final RestClient restClient;
    private final String model;
    private final boolean enabled;

    public RerankService(@Value("${rag.rerank.enabled:true}") boolean enabled,
                         @Value("${rag.rerank.model:gte-rerank}") String model,
                         @Value("${langchain4j.dashscope.chat-model.api-key:}") String apiKey) {
        this.enabled = enabled;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank")
                .defaultHeaders(headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setBearerAuth(apiKey);
                })
                .build();
    }

    /**
     * 对文档列表进行语义重排序。
     *
     * @param query     用户问题
     * @param documents 待排序文档
     * @param topN      返回前 N 个
     * @return 重排序后的结果（保留原始向量/BM25/RRF 分数）
     */
    public List<SearchResult> rerank(String query, List<SearchResult> documents, int topN) {
        if (!enabled || documents.isEmpty()) {
            return documents;
        }

        try {
            String[] docTexts = documents.stream()
                    .map(SearchResult::getText)
                    .toArray(String[]::new);

            RerankRequest request = new RerankRequest();
            request.setModel(model);

            RerankRequest.RerankInput input = new RerankRequest.RerankInput();
            input.setQuery(query);
            input.setDocuments(docTexts);
            request.setInput(input);

            RerankRequest.RerankParameters parameters = new RerankRequest.RerankParameters();
            parameters.setTopN(Math.min(topN, documents.size()));
            request.setParameters(parameters);

            RerankResponse response = restClient.post()
                    .body(request)
                    .retrieve()
                    .body(RerankResponse.class);

            if (response == null || response.getOutput() == null || response.getOutput().getResults() == null) {
                log.warn("[Rerank] API 返回空结果，回退到融合分数");
                return documents;
            }

            // 按 relevanceScore 降序排列，设置 rerankScore 到结果中
            List<RerankResponse.RerankResult> results = response.getOutput().getResults();
            results.sort(Comparator.comparing(RerankResponse.RerankResult::getRelevanceScore).reversed());

            List<SearchResult> reranked = new java.util.ArrayList<>();
            for (RerankResponse.RerankResult r : results) {
                if (r.getIndex() != null && r.getIndex() < documents.size()) {
                    SearchResult original = documents.get(r.getIndex());
                    SearchResult copy = new SearchResult(original.getText(), original.getMetadata());
                    copy.setVectorScore(original.getVectorScore());
                    copy.setBm25Score(original.getBm25Score());
                    copy.setRrfScore(original.getRrfScore());
                    copy.setRerankScore(r.getRelevanceScore());
                    reranked.add(copy);
                }
            }

            log.debug("[Rerank] 完成重排序，输入 {} 条 → 输出 {} 条", documents.size(), reranked.size());
            return reranked;

        } catch (Exception e) {
            log.warn("[Rerank] API 调用失败，回退到融合分数: {}", e.getMessage());
            return documents;
        }
    }

    public boolean isEnabled() { return enabled; }
}
