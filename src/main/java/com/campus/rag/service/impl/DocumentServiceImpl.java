package com.campus.rag.service.impl;

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
import java.util.List;

/**
 * 文档摄入管道实现（Step 5）
 *
 * <p>同步五段式流水线：
 * (a) 物理落盘  →  (b) DB 初始化  →  (c) 解析 + 切片  →  (d) 向量化入库  →  (e) DB 状态更新
 */
@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

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
        DocumentIngestionContext ctx = new DocumentIngestionContext();
        ctx.setRawFile(file);

        // (b) DB 初始化：status=1 代表"处理中"，防止服务重启时被误判为待处理
        Document doc = initDbRecord(userId, file);
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
    private Document initDbRecord(Long userId, MultipartFile file) {
        Document doc = new Document();
        doc.setUserId(userId);
        doc.setFileName(file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "unknown.pdf");
        doc.setFilePath("");          // 落盘后回填
        doc.setFileType(file.getContentType());
        doc.setStatus(1);             // 1 = 处理中
        doc.setCreateTime(LocalDateTime.now());
        doc.setUpdateTime(LocalDateTime.now());
        documentMapper.insert(doc);   // 执行后 doc.getId() 由 MyBatis 自动回填
        return doc;
    }

    /**
     * 文件落盘：目录不存在则自动创建，文件名加 docId 前缀防止冲突
     */
    private Path saveFileToDisk(MultipartFile file, Long docId) throws IOException {
        Path uploadDir = Paths.get(uploadPath);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        String safeFileName = docId + "_" + (file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "document.pdf");
        Path targetPath = uploadDir.resolve(safeFileName);
        file.transferTo(targetPath.toFile());
        return targetPath;
    }

    // =========================================================================
    // 其他接口方法
    // =========================================================================

    @Override
    public List<Document> listByUserId(Long userId) {
        return documentMapper.selectByUserId(userId);
    }

    @Override
    public void deleteById(Long id) {
        documentMapper.deleteById(id);
    }
}
