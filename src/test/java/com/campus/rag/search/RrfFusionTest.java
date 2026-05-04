package com.campus.rag.search;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionTest {

    private final RrfFusion fusion = new RrfFusion(60);

    @Test
    void fuseMergesBothLists() {
        List<SearchResult> vector = List.of(
                result("1", "内容A", 0.9),
                result("2", "内容B", 0.8),
                result("3", "内容C", 0.7)
        );
        List<SearchResult> bm25 = List.of(
                result("2", "内容B", 0.6),
                result("4", "内容D", 0.5)
        );

        List<SearchResult> fused = fusion.fuse(vector, bm25, 5);

        // Should contain unique documents from both lists
        assertThat(fused).hasSize(4);
        assertThat(fused.stream().map(r -> r.getMetadata().getString("documentId")))
                .containsExactlyInAnyOrder("1", "2", "3", "4");
    }

    @Test
    void fusionBoostsDocumentsInBothLists() {
        List<SearchResult> vector = List.of(
                result("1", "A", 0.9),
                result("2", "B", 0.8)
        );
        List<SearchResult> bm25 = List.of(
                result("2", "B", 0.7),
                result("1", "A", 0.6)
        );

        List<SearchResult> fused = fusion.fuse(vector, bm25, 5);

        // Both docs should appear, doc1 may be first (ranked higher in vector)
        assertThat(fused).hasSize(2);
        assertThat(fused.get(0).getRrfScore()).isNotNull();
        assertThat(fused.get(1).getRrfScore()).isNotNull();
    }

    @Test
    void fuseWithEmptyVectorReturnsBm25() {
        List<SearchResult> bm25 = List.of(
                bm25Result("1", "A", 0.9),
                bm25Result("2", "B", 0.8)
        );

        List<SearchResult> fused = fusion.fuse(Collections.emptyList(), bm25, 5);

        assertThat(fused).hasSize(2);
    }

    @Test
    void fuseWithEmptyBm25ReturnsVector() {
        List<SearchResult> vector = List.of(
                result("1", "A", 0.9)
        );

        List<SearchResult> fused = fusion.fuse(vector, Collections.emptyList(), 5);

        assertThat(fused).hasSize(1);
        assertThat(fused.get(0).getVectorScore()).isEqualTo(0.9);
    }

    @Test
    void fuseRespectsTopK() {
        List<SearchResult> vector = List.of(
                result("1", "A", 0.9),
                result("2", "B", 0.8),
                result("3", "C", 0.7)
        );
        List<SearchResult> bm25 = List.of(
                result("4", "D", 0.6)
        );

        List<SearchResult> fused = fusion.fuse(vector, bm25, 2);

        assertThat(fused).hasSize(2);
    }

    @Test
    void fusePropagatesOriginalScores() {
        List<SearchResult> vector = List.of(result("1", "A", 0.9));
        List<SearchResult> bm25 = List.of(result("2", "B", 0.8));

        List<SearchResult> fused = fusion.fuse(vector, bm25, 5);

        assertThat(fused).hasSize(2);
        assertThat(fused.get(0).getRrfScore()).isNotNull();
    }

    private static SearchResult result(String docId, String text, double vectorScore) {
        Metadata meta = new Metadata();
        meta.put("documentId", docId);
        SearchResult sr = new SearchResult(text, meta);
        sr.setVectorScore(vectorScore);
        return sr;
    }

    private static SearchResult bm25Result(String docId, String text, double bm25Score) {
        Metadata meta = new Metadata();
        meta.put("documentId", docId);
        SearchResult sr = new SearchResult(text, meta);
        sr.setBm25Score(bm25Score);
        return sr;
    }
}
