package com.campus.rag.service.impl;

import com.campus.rag.common.exception.BusinessException;
import com.campus.rag.dto.DocumentIngestionContext;
import com.campus.rag.entity.Document;
import com.campus.rag.mapper.DocumentMapper;
import com.campus.rag.service.DocumentIndexingService;
import com.campus.rag.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.List;
import java.util.Locale;

/**
 * 文档摄入管道实现（Step 5）
 *
 * <p>同步五段式流水线：
 * (a) 物理落盘  →  (b) DB 初始化  →  (c) 解析 + 切片  →  (d) 向量化入库  →  (e) DB 状态更新
 */
@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    private final DocumentMapper documentMapper;
    private final DocumentIndexingService documentIndexingService;

    @Value("${app.upload.path}")
    private String uploadPath;

    public DocumentServiceImpl(DocumentMapper documentMapper,
                               DocumentIndexingService documentIndexingService) {
        this.documentMapper = documentMapper;
        this.documentIndexingService = documentIndexingService;
    }

    // =========================================================================
    // 核心：文档上传与摄入管道
    // =========================================================================

    @Override
    public Document upload(Long userId, MultipartFile file) {
        return upload(userId, file, null);
    }

    @Override
    public Document upload(Long userId, MultipartFile file, Long categoryId) {
        validateSupportedDocument(file);

        DocumentIngestionContext ctx = new DocumentIngestionContext();
        ctx.setRawFile(file);

        // (b) DB 初始化：status=1 代表"处理中"，防止服务重启时被误判为待处理
        Document doc = initDbRecord(userId, file, categoryId);
        ctx.setDbDocument(doc);

        try {
            // (a) 物理落盘
            Path savedPath = saveFileToDisk(file, doc.getId());
            ctx.setSavedFilePath(savedPath);
            // 回写真实路径到 DB
            doc.setFilePath(savedPath.toAbsolutePath().toString());
            doc.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(doc);
            log.info("[摄入管道] [文档ID={}] ✅ 步骤 1/4 — 文件落盘成功: {}", doc.getId(), savedPath);

            // (c/d) 解析、切片、向量化并写入 InMemoryEmbeddingStore
            documentIndexingService.index(doc, savedPath);
            log.info("[摄入管道] [文档ID={}] ✅ 步骤 2/2 — 文档索引完成", doc.getId());

            // (e) DB 最终状态：status=2 已完成
            doc.setStatus(2);
            doc.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(doc);
            log.info("[摄入管道] [文档ID={}] 🎉 全流程完成！文档《{}》已就绪，可参与 RAG 检索",
                    doc.getId(), doc.getFileName());

        } catch (Exception e) {
            // 任意步骤异常：状态置为 3（失败），确保数据库不留"幽灵"记录
            log.error("[摄入管道] [文档ID={}] ❌ 摄入失败，原因: {}", doc.getId(), e.getMessage(), e);
            doc.setStatus(3);
            doc.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(doc);
            throw new RuntimeException("文档处理失败：" + e.getMessage(), e);
        }

        return doc;
    }

    // =========================================================================
    // 私有辅助方法
    // =========================================================================

    /**
     * 初始化 DB 记录，status=1（处理中）
     */
    private Document initDbRecord(Long userId, MultipartFile file, Long categoryId) {
        String originalFileName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "unknown.pdf";

        Document doc = new Document();
        doc.setUserId(userId);
        doc.setCategoryId(categoryId);
        doc.setFileName(originalFileName);
        doc.setFilePath("");          // 落盘后回填
        doc.setFileType(getFileExtension(originalFileName));
        doc.setStatus(1);             // 1 = 处理中
        doc.setCreateTime(LocalDateTime.now());
        doc.setUpdateTime(LocalDateTime.now());
        documentMapper.insert(doc);   // 执行后 doc.getId() 由 MyBatis 自动回填
        return doc;
    }

    /**
     * 后端兜底校验，避免绕过前端限制上传不支持的文件。
     */
    private void validateSupportedDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择需要上传的校园资料文件");
        }

        String extension = getFileExtension(file.getOriginalFilename());
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(400, "当前知识库支持 PDF、Word、Excel、PPT 文件；图片 OCR 将在后续阶段支持");
        }
    }

    /**
     * 文件落盘：目录不存在则自动创建，文件名加 docId 前缀防止冲突
     */
    private Path saveFileToDisk(MultipartFile file, Long docId) throws IOException {
        Path uploadDir = Paths.get(uploadPath);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        String safeFileName = docId + "_" + sanitizeFileName(file.getOriginalFilename());
        Path targetPath = uploadDir.resolve(safeFileName);
        file.transferTo(targetPath.toFile());
        return targetPath;
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "document.pdf";
        }

        String normalized = originalFileName.replace("\\", "/");
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replace("..", "")
                .trim();
        return baseName.isBlank() ? "document.pdf" : baseName;
    }

    private String getFileExtension(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "";
        }

        String fileName = originalFileName.toLowerCase(Locale.ROOT);
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }

    // =========================================================================
    // 其他接口方法
    // =========================================================================

    @Override
    public List<Document> listByUserId(Long userId) {
        return documentMapper.selectByUserId(userId);
    }

    @Override
    public List<Document> listAll() {
        return documentMapper.selectAll();
    }

    @Override
    public void deleteById(Long id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(404, "文档不存在或已被删除");
        }

        documentMapper.deleteById(id);
        documentIndexingService.removeByDocumentId(id);

        String filePath = document.getFilePath();
        if (filePath == null || filePath.isBlank()) {
            return;
        }

        try {
            boolean deleted = Files.deleteIfExists(Paths.get(filePath));
            if (deleted) {
                log.info("[文档管理] [文档ID={}] 已删除本地文件: {}", id, filePath);
            }
        } catch (IOException e) {
            log.warn("[文档管理] [文档ID={}] 数据库记录已删除，但本地文件清理失败: {}", id, filePath, e);
        }
    }
}
