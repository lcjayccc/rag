package com.campus.rag.service.impl;

import com.campus.rag.common.exception.BusinessException;
import com.campus.rag.entity.DocumentCategory;
import com.campus.rag.mapper.DocumentCategoryMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentCategoryServiceImplTest {

    @Test
    void createCategoryTrimsInputAndDefaultsEnabled() {
        RecordingDocumentCategoryMapper mapper = new RecordingDocumentCategoryMapper();
        DocumentCategoryServiceImpl service = new DocumentCategoryServiceImpl(mapper);

        DocumentCategory category = service.create(" 教务处 ", " academic ", " 教务业务资料 ", 10);

        assertEquals("教务处", category.getName());
        assertEquals("academic", category.getCode());
        assertEquals("教务业务资料", category.getDescription());
        assertEquals(10, category.getSortOrder());
        assertTrue(category.getEnabled());
        assertNotNull(category.getCreateTime());
        assertNotNull(category.getUpdateTime());
        assertEquals(category, mapper.insertedCategories.getFirst());
    }

    @Test
    void createCategoryRejectsDuplicateCode() {
        RecordingDocumentCategoryMapper mapper = new RecordingDocumentCategoryMapper();
        mapper.categoryByCode = category(1L, "教务处", "academic", true);
        DocumentCategoryServiceImpl service = new DocumentCategoryServiceImpl(mapper);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create("教务处", "academic", "重复分类", 0));

        assertEquals(400, error.getCode());
        assertEquals("分类编码已存在", error.getMessage());
    }

    @Test
    void updateEnabledRejectsMissingCategory() {
        RecordingDocumentCategoryMapper mapper = new RecordingDocumentCategoryMapper();
        DocumentCategoryServiceImpl service = new DocumentCategoryServiceImpl(mapper);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateEnabled(99L, false));

        assertEquals(404, error.getCode());
        assertEquals("分类不存在", error.getMessage());
    }

    private static DocumentCategory category(Long id, String name, String code, boolean enabled) {
        DocumentCategory category = new DocumentCategory();
        category.setId(id);
        category.setName(name);
        category.setCode(code);
        category.setDescription(name + "资料");
        category.setSortOrder(0);
        category.setEnabled(enabled);
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        return category;
    }

    private static class RecordingDocumentCategoryMapper implements DocumentCategoryMapper {
        private final List<DocumentCategory> insertedCategories = new ArrayList<>();
        private DocumentCategory categoryByCode;
        private DocumentCategory categoryById;

        @Override
        public DocumentCategory selectById(Long id) {
            return categoryById != null && categoryById.getId().equals(id) ? categoryById : null;
        }

        @Override
        public DocumentCategory selectByCode(String code) {
            return categoryByCode != null && categoryByCode.getCode().equals(code) ? categoryByCode : null;
        }

        @Override
        public List<DocumentCategory> selectAll() {
            return List.of();
        }

        @Override
        public List<DocumentCategory> selectEnabled() {
            return List.of();
        }

        @Override
        public int insert(DocumentCategory category) {
            category.setId(10L);
            insertedCategories.add(category);
            return 1;
        }

        @Override
        public int updateById(DocumentCategory category) {
            categoryById = category;
            return 1;
        }
    }
}
