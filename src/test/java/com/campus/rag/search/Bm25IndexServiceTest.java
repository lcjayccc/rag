package com.campus.rag.search;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Bm25IndexServiceTest {

    private Bm25IndexService service;

    @BeforeEach
    void setUp() {
        service = new Bm25IndexService();
    }

    @Test
    void searchReturnsEmptyForEmptyIndex() {
        List<SearchResult> results = service.search("三好学生", 5);
        assertThat(results).isEmpty();
    }

    @Test
    void searchReturnsMatchingDocument() {
        addDoc(1L, "河南工业大学校级三好学生审批表申请流程说明");
        addDoc(2L, "学生食堂就餐指南和开放时间安排");

        List<SearchResult> results = service.search("三好学生审批表", 3);

        assertThat(results).hasSizeGreaterThanOrEqualTo(1);
        assertThat(results.get(0).getBm25Score()).isPositive();
    }

    @Test
    void searchReturnsEmptyForOovQuery() {
        addDoc(1L, "校园卡办理流程指南");

        List<SearchResult> results = service.search("量子计算机", 3);

        assertThat(results).isEmpty();
    }

    @Test
    void addAndRemoveDocument() {
        addDoc(1L, "测试文档内容");
        assertThat(service.getTotalDocs()).isEqualTo(1);

        service.removeDocument(1L);
        assertThat(service.getTotalDocs()).isZero();

        List<SearchResult> results = service.search("测试文档", 3);
        assertThat(results).isEmpty();
    }

    @Test
    void buildIndexReplacesExisting() {
        Metadata meta1 = new Metadata();
        meta1.put("documentId", "1");
        SearchResult sr1 = new SearchResult("文档一的内容", meta1);
        service.buildIndex(List.of(sr1));

        assertThat(service.getTotalDocs()).isEqualTo(1);

        Metadata meta2 = new Metadata();
        meta2.put("documentId", "2");
        SearchResult sr2 = new SearchResult("文档二的内容", meta2);
        service.buildIndex(List.of(sr2));

        assertThat(service.getTotalDocs()).isEqualTo(1); // replaces
    }

    @Test
    void clearEmptiesIndex() {
        addDoc(1L, "测试内容");

        service.clear();
        assertThat(service.getTotalDocs()).isZero();
        assertThat(service.search("测试", 3)).isEmpty();
    }

    @Test
    void multipleDocumentsWithOverlappingTerms() {
        addDoc(1L, "奖学金申请条件和评审办法");
        addDoc(2L, "奖学金公示名单和发放流程");
        addDoc(3L, "图书馆开放时间");

        List<SearchResult> results = service.search("奖学金申请", 5);

        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        // doc 1 should rank higher (matches both 奖学金 and 申请)
        String topDocId = results.get(0).getMetadata().getString("documentId");
        assertThat(topDocId).isEqualTo("1");
    }

    @Test
    void bm25ScoreDecreasesForLessRelevantDocs() {
        addDoc(1L, "奖学金申请条件和评审办法详解");
        addDoc(2L, "校园风景优美环境宜人适合学生学习生活");

        List<SearchResult> results = service.search("奖学金", 5);

        assertThat(results).hasSizeGreaterThanOrEqualTo(1);
        double score1 = results.get(0).getBm25Score();
        // doc 2 should have lower or zero score
        double score2 = results.stream()
                .filter(r -> "2".equals(r.getMetadata().getString("documentId")))
                .findFirst()
                .map(SearchResult::getBm25Score)
                .orElse(0.0);
        assertThat(score1).isGreaterThan(score2);
    }

    private void addDoc(Long docId, String text) {
        Metadata meta = new Metadata();
        meta.put("documentId", String.valueOf(docId));
        meta.put("fileName", "test-" + docId + ".txt");
        meta.put("chunkIndex", "0");
        SearchResult sr = new SearchResult(text, meta);
        service.addDocument(docId, sr);
    }
}
