package com.campus.rag.controller;

import com.campus.rag.auth.AuthContext;
import com.campus.rag.auth.AuthPrincipal;
import com.campus.rag.common.Result;
import com.campus.rag.entity.Document;
import com.campus.rag.service.DocumentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传与管理
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public Result<Document> upload(@RequestParam("file") MultipartFile file) {
        AuthPrincipal admin = AuthContext.requireAdmin();
        Document doc = documentService.upload(admin.getUserId(), file);
        return Result.success(doc);
    }

    @GetMapping
    public Result<List<Document>> list() {
        AuthContext.requireAdmin();
        return Result.success(documentService.listAll());
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        AuthContext.requireAdmin();
        documentService.deleteById(id);
        return Result.success();
    }
}
