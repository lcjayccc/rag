package com.campus.rag.chroma;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromaEmbeddingStoreAdapterTest {

    @Test
    void toChromaWhereConvertsIsEqualToFilter() {
        StubChromaClient stubClient = new StubChromaClient();
        ChromaEmbeddingStoreAdapter adapter = new ChromaEmbeddingStoreAdapter(stubClient);

        Filter filter = metadataKey("documentId").isEqualTo("42");
        Map<String, String> where = adapter.toChromaWhere(filter);

        assertEquals(Map.of("documentId", "42"), where);
    }

    @Test
    void toChromaWhereReturnsEmptyForNullFilter() {
        StubChromaClient stubClient = new StubChromaClient();
        ChromaEmbeddingStoreAdapter adapter = new ChromaEmbeddingStoreAdapter(stubClient);

        Map<String, String> where = adapter.toChromaWhere(null);

        assertTrue(where.isEmpty());
    }

    @Test
    void addAllAssignsIdsWithDocumentIdChunkIndexPattern() {
        RecordingChromaClient recordingClient = new RecordingChromaClient();
        ChromaEmbeddingStoreAdapter adapter = new ChromaEmbeddingStoreAdapter(recordingClient);

        TextSegment segment = TextSegment.from("测试文本",
                new dev.langchain4j.data.document.Metadata()
                        .put("documentId", "18")
                        .put("chunkIndex", "3"));
        Embedding embedding = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});

        List<String> ids = adapter.addAll(List.of(embedding), List.of(segment));

        assertEquals(1, ids.size());
        assertEquals("18_3", ids.getFirst());
        assertEquals(1, recordingClient.addedIds.size());
        assertEquals("18_3", recordingClient.addedIds.getFirst());
    }

    @Test
    void searchConvertsDistanceToSimilarityAndFiltersByMinScore() {
        // Prepare stub that returns one result with distance 0.3 → similarity 0.7
        StubChromaClient stubClient = new StubChromaClient();
        stubClient.nextQueryResponse = Map.of(
                "ids", List.of(List.of("18_0")),
                "metadatas", List.of(List.of(Map.of("documentId", "18", "fileName", "test.pdf"))),
                "documents", List.of(List.of("测试文本内容")),
                "distances", List.of(List.of(0.3))
        );
        ChromaEmbeddingStoreAdapter adapter = new ChromaEmbeddingStoreAdapter(stubClient);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[]{0.1f, 0.2f, 0.3f}))
                .maxResults(3)
                .minScore(0.65)
                .build();

        EmbeddingSearchResult<TextSegment> result = adapter.search(request);

        assertEquals(1, result.matches().size());
        EmbeddingMatch<TextSegment> match = result.matches().getFirst();
        assertEquals(0.7, match.score(), 0.001);
        assertEquals("测试文本内容", match.embedded().text());
        assertEquals("18", match.embedded().metadata().getString("documentId"));
    }

    @Test
    void searchFiltersOutResultsBelowMinScore() {
        StubChromaClient stubClient = new StubChromaClient();
        stubClient.nextQueryResponse = Map.of(
                "ids", List.of(List.of("18_0", "19_0")),
                "metadatas", List.of(List.of(
                        Map.of("documentId", "18"),
                        Map.of("documentId", "19")
                )),
                "documents", List.of(List.of("文本A", "文本B")),
                "distances", List.of(List.of(0.3, 0.5)) // similarity: 0.7, 0.5
        );
        ChromaEmbeddingStoreAdapter adapter = new ChromaEmbeddingStoreAdapter(stubClient);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[]{0.1f, 0.2f}))
                .maxResults(3)
                .minScore(0.6)
                .build();

        EmbeddingSearchResult<TextSegment> result = adapter.search(request);

        // Only the first one (0.7) passes minScore=0.6, second (0.5) is filtered out
        assertEquals(1, result.matches().size());
        assertEquals("文本A", result.matches().getFirst().embedded().text());
    }

    @Test
    void searchPassesFilterToChromaWhere() {
        RecordingChromaClient recordingClient = new RecordingChromaClient();
        recordingClient.nextQueryResponse = Map.of(
                "ids", List.of(List.of()),
                "metadatas", List.of(List.of()),
                "documents", List.of(List.of()),
                "distances", List.of(List.of())
        );
        ChromaEmbeddingStoreAdapter adapter = new ChromaEmbeddingStoreAdapter(recordingClient);

        Filter filter = metadataKey("categoryId").isEqualTo("7");
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[]{0.1f, 0.2f}))
                .maxResults(3)
                .minScore(0.5)
                .filter(filter)
                .build();

        adapter.search(request);

        assertNotNull(recordingClient.lastQueryWhere);
        assertEquals(Map.of("categoryId", "7"), recordingClient.lastQueryWhere);
    }

    @Test
    void removeAllDelegatesWithConvertedFilter() {
        RecordingChromaClient recordingClient = new RecordingChromaClient();
        ChromaEmbeddingStoreAdapter adapter = new ChromaEmbeddingStoreAdapter(recordingClient);

        Filter filter = metadataKey("documentId").isEqualTo("42");
        adapter.removeAll(filter);

        assertNotNull(recordingClient.lastDeleteWhere);
        assertEquals(Map.of("documentId", "42"), recordingClient.lastDeleteWhere);
    }

    @Test
    void addAllWithEmptyListReturnsEmpty() {
        StubChromaClient stubClient = new StubChromaClient();
        ChromaEmbeddingStoreAdapter adapter = new ChromaEmbeddingStoreAdapter(stubClient);

        List<String> ids = adapter.addAll(Collections.emptyList());

        assertTrue(ids.isEmpty());
    }

    // ---- stub / recording implementations ----

    private static class StubChromaClient extends ChromaClient {
        Map<String, Object> nextQueryResponse;

        StubChromaClient() {
            super(new ChromaProperties());
        }

        @Override
        public void ensureCollection() {}

        @Override
        public void add(List<String> ids, List<float[]> embeddings,
                        List<Map<String, String>> metadatas, List<String> documents) {}

        @Override
        public Map<String, Object> query(float[] queryEmbedding, int nResults,
                                         Map<String, String> where) {
            return nextQueryResponse;
        }

        @Override
        public void delete(Map<String, String> where) {}
    }

    private static class RecordingChromaClient extends ChromaClient {
        List<String> addedIds = new java.util.ArrayList<>();
        Map<String, String> lastQueryWhere;
        Map<String, String> lastDeleteWhere;
        Map<String, Object> nextQueryResponse = Map.of(
                "ids", List.of(List.of()),
                "metadatas", List.of(List.of()),
                "documents", List.of(List.of()),
                "distances", List.of(List.of())
        );

        RecordingChromaClient() {
            super(new ChromaProperties());
        }

        @Override
        public void ensureCollection() {}

        @Override
        public void add(List<String> ids, List<float[]> embeddings,
                        List<Map<String, String>> metadatas, List<String> documents) {
            addedIds = ids;
        }

        @Override
        public Map<String, Object> query(float[] queryEmbedding, int nResults,
                                         Map<String, String> where) {
            this.lastQueryWhere = where;
            return nextQueryResponse;
        }

        @Override
        public void delete(Map<String, String> where) {
            this.lastDeleteWhere = where;
        }
    }
}
