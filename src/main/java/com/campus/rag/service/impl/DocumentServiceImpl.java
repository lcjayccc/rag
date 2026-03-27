package com.campus.rag.service.impl;

import com.campus.rag.dto.DocumentIngestionContext;
import com.campus.rag.entity.Document;
import com.campus.rag.mapper.DocumentMapper;
import com.campus.rag.service.DocumentService;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final EmbeddingModel embeddingModel;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;

    @Value("${app.upload.path}")
    private String uploadPath;

    public DocumentServiceImpl(DocumentMapper documentMapper,
                               EmbeddingModel embeddingModel,
                               InMemoryEmbeddingStore<TextSegment> embeddingStore) {
        this.documentMapper = documentMapper;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
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

            // (c-1) 解析 PDF → LangChain4j Document
            dev.langchain4j.data.document.Document lcDoc = parseDocument(savedPath);
            ctx.setParsedDocument(lcDoc);
            log.info("[摄入管道] [文档ID={}] ✅ 步骤 2/4 — PDF 解析完成，原始文本长度: {} 字符",
                    doc.getId(), lcDoc.text().length());

            // (c-2) 递归切片（500字符/块，50字符重叠），并注入溯源元数据
            List<TextSegment> chunks = splitAndEnrich(lcDoc, doc);
            ctx.setChunks(chunks);
            log.info("[摄入管道] [文档ID={}] ✅ 步骤 3/4 — 切片完成，共 {} 个 Chunk（策略: recursive 500/50）",
                    doc.getId(), chunks.size());

            // (d) 批量向量化，写入 InMemoryEmbeddingStore
            List<Embedding> embeddings = embeddingModel.embedAll(chunks).content();
            ctx.setEmbeddings(embeddings);
            embeddingStore.addAll(embeddings, chunks);
            log.info("[摄入管道] [文档ID={}] ✅ 步骤 4/4 — 向量化完成，向量维度: {}，已写入 EmbeddingStore",
                    doc.getId(), embeddings.isEmpty() ? 0 : embeddings.get(0).vector().length);

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

    /**
     * 使用 Apache PDFBox 解析 PDF 文件为 LangChain4j Document
     */
    private dev.langchain4j.data.document.Document parseDocument(Path filePath) {
        return FileSystemDocumentLoader.loadDocument(filePath, new ApachePdfBoxDocumentParser());
    }

    /**
     * 递归切片，并为每个 Chunk 注入溯源元数据（documentId / fileName / chunkIndex）
     */
    private List<TextSegment> splitAndEnrich(dev.langchain4j.data.document.Document lcDoc,
                                             Document dbDoc) {
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
        List<TextSegment> rawChunks = splitter.split(lcDoc);

        List<TextSegment> enriched = new ArrayList<>(rawChunks.size());
        for (int i = 0; i < rawChunks.size(); i++) {
            Metadata meta = new Metadata();
            meta.put("documentId", String.valueOf(dbDoc.getId()));
            meta.put("fileName", dbDoc.getFileName());
            meta.put("chunkIndex", String.valueOf(i));
            enriched.add(TextSegment.from(rawChunks.get(i).text(), meta));
        }
        return enriched;
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
