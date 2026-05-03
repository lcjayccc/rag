package com.campus.rag.controller;

import com.campus.rag.auth.AuthContext;
import com.campus.rag.common.Result;
import com.campus.rag.entity.DocumentCategory;
import com.campus.rag.service.DocumentCategoryService;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库分类管理接口。
 */
@RestController
@RequestMapping("/api/document-categories")
public class DocumentCategoryController {

    private final DocumentCategoryService service;

    public DocumentCategoryController(DocumentCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<DocumentCategory>> listEnabled() {
        AuthContext.requireLogin();
        return Result.success(service.listEnabled());
    }

    @GetMapping("/all")
    public Result<List<DocumentCategory>> listAll() {
        AuthContext.requireAdmin();
        return Result.success(service.listAll());
    }

    @PostMapping
    public Result<DocumentCategory> create(@RequestBody CreateRequest request) {
        AuthContext.requireAdmin();
        return Result.success(service.create(
                request.getName(),
                request.getCode(),
                request.getDescription(),
                request.getSortOrder()
        ));
    }

    @PatchMapping("/{id}/enabled")
    public Result<DocumentCategory> updateEnabled(@PathVariable Long id, @RequestBody EnabledRequest request) {
        AuthContext.requireAdmin();
        return Result.success(service.updateEnabled(id, request.getEnabled()));
    }

    @Data
    public static class CreateRequest {
        private String name;
        private String code;
        private String description;
        private Integer sortOrder;
    }

    @Data
    public static class EnabledRequest {
        private Boolean enabled;
    }
}
