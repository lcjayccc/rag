package com.campus.rag.chroma;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Chroma REST API 底层客户端（v2 API, Chroma >= 1.0.0）。
 *
 * <p>封装 add / query / delete 操作，由 ChromaEmbeddingStoreAdapter 委托调用。
 * 向量 ID 格式：{documentId}_{chunkIndex}，确保可追溯。
 *
 * <p>Chroma 1.0.0+ 使用 CRN 格式 {tenant}/{database}/{collection}。
 * 默认 tenant=default_tenant, database=default_database。
 * 数据操作（add/query/delete）需要 collection UUID，在 ensureCollection 后缓存。
 */
@Slf4j
@Component
public class ChromaClient {

    private final RestClient restClient;
    private final String tenant;
    private final String database;
    private final String collectionName;
    private final String collectionPath; // tenant/database/collections/{name}
    private volatile String collectionId; // UUID, initialized by ensureCollection()

    public ChromaClient(ChromaProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeaders(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
                .build();
        this.tenant = props.getTenant();
        this.database = props.getDatabase();
        this.collectionName = props.getCollectionName();
        this.collectionPath = tenant + "/" + database + "/collections/" + collectionName;
    }

    public String getCollectionName() {
        return collectionName;
    }

    /**
     * 确保 collection 存在并缓存其 UUID。Chroma 1.0+ 也会自动创建 tenant 和 database。
     */
    @SuppressWarnings("unchecked")
    public void ensureCollection() {
        Map<String, Object> collInfo = getCollection();
        if (collInfo != null) {
            this.collectionId = (String) collInfo.get("id");
            log.info("[Chroma] collection 已存在: {} (id={})", collectionPath, this.collectionId);
            return;
        }

        try {
            Map<String, Object> created = restClient.post()
                    .uri("/api/v2/tenants/{tenant}/databases/{database}/collections",
                            tenant(), database())
                    .body(Map.of("name", coll(),
                            "metadata", Map.of("hnsw:space", "cosine")))
                    .retrieve()
                    .body(Map.class);
            this.collectionId = (String) created.get("id");
            log.info("[Chroma] 已创建 collection: {} (id={})", collectionPath, this.collectionId);
        } catch (Exception e) {
            log.warn("[Chroma] 创建 collection 异常: {}", e.getMessage());
            collInfo = getCollection();
            if (collInfo != null) {
                this.collectionId = (String) collInfo.get("id");
                log.info("[Chroma] collection 已存在（get 确认）: {} (id={})", collectionPath, this.collectionId);
            } else {
                log.error("[Chroma] 无法创建或访问 collection: {}", e.getMessage());
                throw new RuntimeException("Chroma collection init failed", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getCollection() {
        try {
            return restClient.get()
                    .uri("/api/v2/tenants/{tenant}/databases/{database}/collections/{collection}",
                            tenant(), database(), coll())
                    .retrieve()
                    .body(Map.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void add(List<String> ids, List<float[]> embeddings,
                    List<Map<String, String>> metadatas, List<String> documents) {
        Map<String, Object> body = Map.of(
                "ids", ids,
                "embeddings", embeddings,
                "metadatas", metadatas,
                "documents", documents
        );
        restClient.post()
                .uri("/api/v2/tenants/{tenant}/databases/{database}/collections/{collectionId}/add",
                        tenant(), database(), collId())
                .body(body)
                .retrieve()
                .toBodilessEntity();
        log.debug("[Chroma] 已添加 {} 条向量", ids.size());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> query(float[] queryEmbedding, int nResults,
                                     Map<String, String> where) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("query_embeddings", List.of(queryEmbedding));
        body.put("n_results", nResults);
        body.put("include", List.of("metadatas", "documents", "distances"));
        if (where != null && !where.isEmpty()) {
            body.put("where", where);
        }
        return restClient.post()
                .uri("/api/v2/tenants/{tenant}/databases/{database}/collections/{collectionId}/query",
                        tenant(), database(), collId())
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    public void delete(Map<String, String> where) {
        if (where == null || where.isEmpty()) {
            return;
        }
        restClient.post()
                .uri("/api/v2/tenants/{tenant}/databases/{database}/collections/{collectionId}/delete",
                        tenant(), database(), collId())
                .body(Map.of("where", where))
                .retrieve()
                .toBodilessEntity();
        log.debug("[Chroma] 已按条件删除: {}", where);
    }

    private String tenant() { return tenant; }
    private String database() { return database; }
    private String coll() { return collectionName; }
    private String collId() { return collectionId; }
}
