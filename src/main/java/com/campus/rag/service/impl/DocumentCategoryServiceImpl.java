package com.campus.rag.service.impl;

import com.campus.rag.common.exception.BusinessException;
import com.campus.rag.entity.DocumentCategory;
import com.campus.rag.mapper.DocumentCategoryMapper;
import com.campus.rag.service.DocumentCategoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentCategoryServiceImpl implements DocumentCategoryService {

    private final DocumentCategoryMapper mapper;

    public DocumentCategoryServiceImpl(DocumentCategoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<DocumentCategory> listEnabled() {
        return mapper.selectEnabled();
    }

    @Override
    public List<DocumentCategory> listAll() {
        return mapper.selectAll();
    }

    @Override
    public DocumentCategory create(String name, String code, String description, Integer sortOrder) {
        String normalizedName = requireText(name, "分类名称不能为空");
        String normalizedCode = requireText(code, "分类编码不能为空").toLowerCase(Locale.ROOT);
        if (mapper.selectByCode(normalizedCode) != null) {
            throw new BusinessException(400, "分类编码已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        DocumentCategory category = new DocumentCategory();
        category.setName(normalizedName);
        category.setCode(normalizedCode);
        category.setDescription(trimToNull(description));
        category.setSortOrder(sortOrder == null ? 0 : sortOrder);
        category.setEnabled(true);
        category.setCreateTime(now);
        category.setUpdateTime(now);
        mapper.insert(category);
        return category;
    }

    @Override
    public DocumentCategory updateEnabled(Long id, Boolean enabled) {
        DocumentCategory category = mapper.selectById(id);
        if (category == null) {
            throw new BusinessException(404, "分类不存在");
        }
        category.setEnabled(enabled != null && enabled);
        category.setUpdateTime(LocalDateTime.now());
        mapper.updateById(category);
        return category;
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(400, message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
