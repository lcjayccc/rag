package com.campus.rag.service.impl;

import com.campus.rag.entity.Document;
import com.campus.rag.mapper.DocumentMapper;
import com.campus.rag.service.DocumentIndexingService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DocumentServiceImplTest {

    @Test
    void uploadPersistsCategoryIdForLaterMetadataFiltering() throws Exception {
        Path uploadDir = Files.createTempDirectory("campus-rag-category-upload");
        RecordingDocumentMapper mapper = new RecordingDocumentMapper(null);
        RecordingIndexingService indexingService = new RecordingIndexingService();
        DocumentServiceImpl service = new DocumentServiceImpl(mapper, indexingService);
        ReflectionTestUtils.setField(service, "uploadPath", uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                "校园制度".getBytes()
        );

        Document uploaded = service.upload(1L, file, 7L);

        assertEquals(7L, mapper.insertedDocument.getCategoryId());
        assertEquals(7L, indexingService.indexedDocuments.getFirst().getCategoryId());
        assertEquals(7L, uploaded.getCategoryId());
    }

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
        private final List<Document> indexedDocuments = new ArrayList<>();

        @Override
        public void index(Document document, Path filePath) {
            indexedDocuments.add(document);
        }

        @Override
        public void removeByDocumentId(Long documentId) {
            removedDocumentIds.add(documentId);
        }
    }

    private static class RecordingDocumentMapper implements DocumentMapper {
        private final Document document;
        private final List<Long> deletedDocumentIds = new ArrayList<>();
        private Document insertedDocument;

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
            document.setId(100L);
            insertedDocument = document;
            return 1;
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

        @Override public int countAll() { return 0; }
        @Override public int countByStatus(int status) { return 0; }
        @Override public List<Map<String, Object>> countGroupByCategory() { return List.of(); }
        @Override public List<Document> selectByStatusAndCategory(int status, Long categoryId) { return List.of(); }
    }
}
