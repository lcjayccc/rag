package com.campus.rag.chroma;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chroma 向量存储适配器，实现 {@link EmbeddingStore} 接口。
 *
 * <p>所有业务代码继续依赖 {@code EmbeddingStore<TextSegment>} 接口注入，
 * 切换到此适配器时零代码感知。
 *
 * <p>向量 ID 格式：{documentId}_{chunkIndex}，与 InMemory 版本一致。
 */
@Slf4j
public class ChromaEmbeddingStoreAdapter implements EmbeddingStore<TextSegment> {

    private final ChromaClient chromaClient;

    public ChromaEmbeddingStoreAdapter(ChromaClient chromaClient) {
        this.chromaClient = chromaClient;
    }

    @Override
    public void add(String id, Embedding embedding) {
        log.warn("[Chroma] add(id, embedding) 不支持，请使用 addAll(List<Embedding>, List<TextSegment>)");
    }

    @Override
    public String add(Embedding embedding, TextSegment segment) {
        List<String> ids = addAll(List.of(embedding), List.of(segment));
        return ids.isEmpty() ? null : ids.getFirst();
    }

    @Override
    public String add(Embedding embedding) {
        return add(embedding, null);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        if (embeddings.isEmpty()) {
            return Collections.emptyList();
        }
        throw new UnsupportedOperationException(
                "Chroma 不支持无 TextSegment 的 addAll；请使用 addAll(List<Embedding>, List<TextSegment>)");
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> segments) {
        if (embeddings.isEmpty()) {
            return Collections.emptyList();
        }
        if (segments.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                    "embeddings 与 segments 数量不一致: " + embeddings.size() + " vs " + segments.size());
        }

        List<String> ids = new ArrayList<>(embeddings.size());
        List<float[]> vectors = new ArrayList<>(embeddings.size());
        List<Map<String, String>> metadatas = new ArrayList<>(embeddings.size());
        List<String> documents = new ArrayList<>(embeddings.size());

        for (int i = 0; i < embeddings.size(); i++) {
            TextSegment segment = segments.get(i);
            String id = buildId(segment);
            ids.add(id);
            vectors.add(embeddingToFloatArray(embeddings.get(i)));
            metadatas.add(toChromaMetadata(segment));
            documents.add(segment.text());
        }

        chromaClient.add(ids, vectors, metadatas, documents);
        return ids;
    }

    @Override
    public void removeAll() {
        log.warn("[Chroma] removeAll() 未实现全量清空，请使用按 documentId 删除");
    }

    @Override
    public void removeAll(Filter filter) {
        Map<String, String> where = toChromaWhere(filter);
        chromaClient.delete(where);
    }

    @SuppressWarnings("unchecked")
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        float[] queryVector = embeddingToFloatArray(request.queryEmbedding());
        int topK = request.maxResults();
        double minScore = request.minScore();

        Map<String, String> where = null;
        if (request.filter() != null) {
            where = toChromaWhere(request.filter());
        }

        Map<String, Object> response = chromaClient.query(queryVector, topK, where);

        List<List<String>> idsList = (List<List<String>>) response.get("ids");
        List<List<Map<String, String>>> metadatasList =
                (List<List<Map<String, String>>>) response.get("metadatas");
        List<List<String>> documentsList = (List<List<String>>) response.get("documents");
        List<List<Double>> distancesList = (List<List<Double>>) response.get("distances");

        if (idsList == null || idsList.isEmpty()) {
            return new EmbeddingSearchResult<>(Collections.emptyList());
        }

        List<String> ids = idsList.getFirst();
        List<Map<String, String>> metadatas = metadatasList != null && !metadatasList.isEmpty()
                ? metadatasList.getFirst() : Collections.emptyList();
        List<String> documents = documentsList != null && !documentsList.isEmpty()
                ? documentsList.getFirst() : Collections.emptyList();
        List<Double> distances = distancesList != null && !distancesList.isEmpty()
                ? distancesList.getFirst() : Collections.emptyList();

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            double distance = (distances != null && i < distances.size()) ? distances.get(i) : 0.0;
            double similarity = 1.0 - distance;

            if (similarity < minScore) {
                continue;
            }

            Map<String, String> meta = (metadatas != null && i < metadatas.size())
                    ? metadatas.get(i) : Collections.emptyMap();
            String text = (documents != null && i < documents.size()) ? documents.get(i) : "";

            dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata();
            meta.forEach(metadata::put);

            TextSegment segment = TextSegment.from(text, metadata);
            EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(
                    similarity,
                    ids.get(i),
                    null, // embedding not needed
                    segment
            );
            matches.add(match);
        }

        return new EmbeddingSearchResult<>(matches);
    }

    // ---- helper methods ----

    private String buildId(TextSegment segment) {
        String docId = segment.metadata().getString("documentId");
        String chunkIdx = segment.metadata().getString("chunkIndex");
        if (docId != null && chunkIdx != null) {
            return docId + "_" + chunkIdx;
        }
        return String.valueOf(System.nanoTime());
    }

    private float[] embeddingToFloatArray(Embedding embedding) {
        return embedding.vector();
    }

    private Map<String, String> toChromaMetadata(TextSegment segment) {
        Map<String, String> meta = new HashMap<>();
        segment.metadata().toMap().forEach((k, v) -> {
            if (v != null) {
                meta.put(k, v.toString());
            }
        });
        return meta;
    }

    Map<String, String> toChromaWhere(Filter filter) {
        if (filter == null) {
            return Collections.emptyMap();
        }
        if (filter instanceof IsEqualTo eq) {
            if (eq.comparisonValue() == null) {
                return Collections.emptyMap();
            }
            return Map.of(eq.key(), eq.comparisonValue().toString());
        }
        log.warn("[Chroma] 不支持的 Filter 类型: {}，将忽略过滤条件", filter.getClass().getSimpleName());
        return Collections.emptyMap();
    }
}
