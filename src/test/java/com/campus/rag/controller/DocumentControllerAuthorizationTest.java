package com.campus.rag.controller;

import com.campus.rag.auth.AuthContext;
import com.campus.rag.auth.AuthPrincipal;
import com.campus.rag.common.exception.BusinessException;
import com.campus.rag.entity.Document;
import com.campus.rag.entity.UserRole;
import com.campus.rag.service.DocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentControllerAuthorizationTest {

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void userCannotDeleteDocument() {
        DocumentController controller = new DocumentController(new RecordingDocumentService());
        AuthContext.set(new AuthPrincipal(2L, "student", UserRole.USER));

        BusinessException error = assertThrows(BusinessException.class, () -> controller.delete(99L));

        assertEquals(403, error.getCode());
        assertEquals("当前用户无权管理知识库", error.getMessage());
    }

    @Test
    void adminCanDeleteDocument() {
        RecordingDocumentService service = new RecordingDocumentService();
        DocumentController controller = new DocumentController(service);
        AuthContext.set(new AuthPrincipal(1L, "admin", UserRole.ADMIN));

        controller.delete(99L);

        assertEquals(List.of(99L), service.deletedIds);
    }

    @Test
    void adminUploadPassesCategoryIdToService() {
        RecordingDocumentService service = new RecordingDocumentService();
        DocumentController controller = new DocumentController(service);
        AuthContext.set(new AuthPrincipal(1L, "admin", UserRole.ADMIN));

        controller.upload(null, 7L);

        assertEquals(7L, service.uploadedCategoryId);
    }

    @Test
    void adminListsAllDocuments() {
        RecordingDocumentService service = new RecordingDocumentService();
        DocumentController controller = new DocumentController(service);
        AuthContext.set(new AuthPrincipal(1L, "admin", UserRole.ADMIN));

        controller.list();

        assertEquals(1, service.listAllCallCount);
    }

    private static class RecordingDocumentService implements DocumentService {
        private final List<Long> deletedIds = new ArrayList<>();
        private Long uploadedCategoryId;

        @Override
        public Document upload(Long userId, MultipartFile file) {
            Document document = new Document();
            document.setUserId(userId);
            return document;
        }

        @Override
        public Document upload(Long userId, MultipartFile file, Long categoryId) {
            uploadedCategoryId = categoryId;
            Document document = new Document();
            document.setUserId(userId);
            document.setCategoryId(categoryId);
            return document;
        }

        @Override
        public List<Document> listByUserId(Long userId) {
            return List.of();
        }

        private int listAllCallCount;

        @Override
        public List<Document> listAll() {
            listAllCallCount++;
            return List.of();
        }

        @Override
        public void deleteById(Long id) {
            deletedIds.add(id);
        }
    }
}
