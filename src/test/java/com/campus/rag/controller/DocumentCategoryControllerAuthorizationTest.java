package com.campus.rag.controller;

import com.campus.rag.auth.AuthContext;
import com.campus.rag.auth.AuthPrincipal;
import com.campus.rag.common.exception.BusinessException;
import com.campus.rag.entity.DocumentCategory;
import com.campus.rag.entity.UserRole;
import com.campus.rag.service.DocumentCategoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentCategoryControllerAuthorizationTest {

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void loggedInUserCanListEnabledCategories() {
        RecordingDocumentCategoryService service = new RecordingDocumentCategoryService();
        DocumentCategoryController controller = new DocumentCategoryController(service);
        AuthContext.set(new AuthPrincipal(2L, "student", UserRole.USER));

        controller.listEnabled();

        assertEquals(1, service.listEnabledCallCount);
    }

    @Test
    void userCannotCreateCategory() {
        DocumentCategoryController controller = new DocumentCategoryController(new RecordingDocumentCategoryService());
        AuthContext.set(new AuthPrincipal(2L, "student", UserRole.USER));
        DocumentCategoryController.CreateRequest request = new DocumentCategoryController.CreateRequest();
        request.setName("教务处");
        request.setCode("academic");

        BusinessException error = assertThrows(BusinessException.class, () -> controller.create(request));

        assertEquals(403, error.getCode());
    }

    @Test
    void adminCanCreateCategory() {
        RecordingDocumentCategoryService service = new RecordingDocumentCategoryService();
        DocumentCategoryController controller = new DocumentCategoryController(service);
        AuthContext.set(new AuthPrincipal(1L, "admin", UserRole.ADMIN));
        DocumentCategoryController.CreateRequest request = new DocumentCategoryController.CreateRequest();
        request.setName("学生工作处");
        request.setCode("student_affairs");
        request.setDescription("学生工作资料");
        request.setSortOrder(20);

        controller.create(request);

        assertEquals("学生工作处", service.createdName);
        assertEquals("student_affairs", service.createdCode);
        assertEquals("学生工作资料", service.createdDescription);
        assertEquals(20, service.createdSortOrder);
    }

    private static class RecordingDocumentCategoryService implements DocumentCategoryService {
        private int listEnabledCallCount;
        private String createdName;
        private String createdCode;
        private String createdDescription;
        private Integer createdSortOrder;

        @Override
        public List<DocumentCategory> listEnabled() {
            listEnabledCallCount++;
            return List.of();
        }

        @Override
        public List<DocumentCategory> listAll() {
            return List.of();
        }

        @Override
        public DocumentCategory create(String name, String code, String description, Integer sortOrder) {
            createdName = name;
            createdCode = code;
            createdDescription = description;
            createdSortOrder = sortOrder;
            return new DocumentCategory();
        }

        @Override
        public DocumentCategory updateEnabled(Long id, Boolean enabled) {
            return new DocumentCategory();
        }
    }
}
