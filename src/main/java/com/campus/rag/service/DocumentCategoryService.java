package com.campus.rag.service;

import com.campus.rag.entity.DocumentCategory;

import java.util.List;

public interface DocumentCategoryService {

    /**
     * 查询启用中的分类，供上传资料和问答范围选择使用。
     */
    List<DocumentCategory> listEnabled();

    /**
     * 管理端查询全部分类，包含已停用分类。
     */
    List<DocumentCategory> listAll();

    /**
     * 新增知识库分类。
     */
    DocumentCategory create(String name, String code, String description, Integer sortOrder);

    /**
     * 启用或停用分类。
     */
    DocumentCategory updateEnabled(Long id, Boolean enabled);
}
