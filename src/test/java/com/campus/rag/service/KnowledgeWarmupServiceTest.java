package com.campus.rag.service;

import com.campus.rag.entity.Document;
import com.campus.rag.mapper.DocumentMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeWarmupServiceTest {

    @Test
    void warmupIndexesOnlyCompletedDocumentsWithExistingFiles() throws Exception {
        Path existingFile = Files.createTempFile("campus-rag-warmup", ".pdf");
        Document ready = document(1L, "ready.pdf", existingFile.toString(), 2);
        Document missing = document(2L, "missing.pdf", existingFile.resolveSibling("missing.pdf").toString(), 2);

        RecordingIndexer indexer = new RecordingIndexer();
        KnowledgeWarmupService warmupService = new KnowledgeWarmupService(
                new FakeDocumentMapper(List.of(ready, missing)),
                indexer
        );

        warmupService.warmup();

        assertEquals(List.of(ready), indexer.indexedDocuments);
    }

    private static Document document(Long id, String fileName, String filePath, Integer status) {
        Document document = new Document();
        document.setId(id);
        document.setFileName(fileName);
        document.setFilePath(filePath);
        document.setStatus(status);
        return document;
    }

    private static class RecordingIndexer implements DocumentIndexingService {
        private final List<Document> indexedDocuments = new ArrayList<>();

        @Override
        public void index(Document document, Path filePath) {
            indexedDocuments.add(document);
        }

        @Override
        public void removeByDocumentId(Long documentId) {
        }
    }

    private static class FakeDocumentMapper implements DocumentMapper {
        private final List<Document> completedDocuments;

        private FakeDocumentMapper(List<Document> completedDocuments) {
            this.completedDocuments = completedDocuments;
        }

        @Override
        public Document selectById(Long id) {
            return null;
        }

        @Override
        public List<Document> selectByUserId(Long userId) {
            return List.of();
        }

        @Override
        public List<Document> selectCompleted() {
            return completedDocuments;
        }

        @Override
        public int insert(Document document) {
            return 0;
        }

        @Override
        public int updateById(Document document) {
            return 0;
        }

        @Override
        public int deleteById(Long id) {
            return 0;
        }
    }
}
