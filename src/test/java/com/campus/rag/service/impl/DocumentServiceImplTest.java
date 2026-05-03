package com.campus.rag.service.impl;

import com.campus.rag.entity.Document;
import com.campus.rag.mapper.DocumentMapper;
import com.campus.rag.service.DocumentIndexingService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DocumentServiceImplTest {

    @Test
    void deleteByIdRemovesDbRecordLocalFileAndIndexedSegments() throws Exception {
        Path file = Files.createTempFile("campus-rag-delete", ".docx");
        Document document = new Document();
        document.setId(42L);
        document.setFileName("delete-test.docx");
        document.setFilePath(file.toString());
        document.setStatus(2);

        RecordingDocumentMapper mapper = new RecordingDocumentMapper(document);
        RecordingIndexingService indexingService = new RecordingIndexingService();
        DocumentServiceImpl service = new DocumentServiceImpl(mapper, indexingService);

        service.deleteById(42L);

        assertEquals(List.of(42L), mapper.deletedDocumentIds);
        assertEquals(List.of(42L), indexingService.removedDocumentIds);
        assertFalse(Files.exists(file));
    }

    private static class RecordingIndexingService implements DocumentIndexingService {
        private final List<Long> removedDocumentIds = new ArrayList<>();

        @Override
        public void index(Document document, Path filePath) {
        }

        @Override
        public void removeByDocumentId(Long documentId) {
            removedDocumentIds.add(documentId);
        }
    }

    private static class RecordingDocumentMapper implements DocumentMapper {
        private final Document document;
        private final List<Long> deletedDocumentIds = new ArrayList<>();

        private RecordingDocumentMapper(Document document) {
            this.document = document;
        }

        @Override
        public Document selectById(Long id) {
            return document;
        }

        @Override
        public List<Document> selectByUserId(Long userId) {
            return List.of();
        }

        @Override
        public List<Document> selectAll() {
            return List.of();
        }

        @Override
        public List<Document> selectCompleted() {
            return List.of();
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
            deletedDocumentIds.add(id);
            return 1;
        }
    }
}
