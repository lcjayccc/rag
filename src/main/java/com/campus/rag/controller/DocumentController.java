package com.campus.rag.controller;

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
    public Result<Document> upload(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                   @RequestParam("file") MultipartFile file) {
        if (userId == null) {
            userId = 1L;
        }
        Document doc = documentService.upload(userId, file);
        return Result.success(doc);
    }

    @GetMapping
    public Result<List<Document>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            userId = 1L;
        }
        return Result.success(documentService.listByUserId(userId));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        documentService.deleteById(id);
        return Result.success();
    }
}
