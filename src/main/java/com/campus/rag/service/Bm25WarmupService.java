package com.campus.rag.service;

import com.campus.rag.entity.Document;
import com.campus.rag.mapper.DocumentMapper;
import com.campus.rag.search.Bm25IndexService;
import com.campus.rag.search.SearchResult;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 启动时从已完成文档重建 BM25 内存索引。不受 rag.vector.store 模式影响，
 * BM25 索引始终是内存的，每次重启需重建。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "rag.vector.store", havingValue = "chroma")
public class Bm25WarmupService {

    private static final Set<String> OFFICE_EXTENSIONS = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    private final DocumentMapper documentMapper;
    private final Bm25IndexService bm25IndexService;

    public Bm25WarmupService(DocumentMapper documentMapper,
                              Bm25IndexService bm25IndexService) {
        this.documentMapper = documentMapper;
        this.bm25IndexService = bm25IndexService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        List<Document> completedDocuments = documentMapper.selectCompleted();
        log.info("[BM25 预热] 开始重建 BM25 索引，待处理文档数: {}", completedDocuments.size());

        int successCount = 0;
        for (Document document : completedDocuments) {
            if (!StringUtils.hasText(document.getFilePath())) continue;

            Path filePath = Paths.get(document.getFilePath());
            if (!Files.exists(filePath)) continue;

            try {
                var lcDoc = FileSystemDocumentLoader.loadDocument(filePath, parserFor(filePath));
                DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
                List<TextSegment> chunks = splitter.split(lcDoc);

                for (int i = 0; i < chunks.size(); i++) {
                    Metadata meta = new Metadata();
                    meta.put("documentId", String.valueOf(document.getId()));
                    meta.put("fileName", document.getFileName());
                    meta.put("chunkIndex", String.valueOf(i));
                    if (document.getCategoryId() != null) {
                        meta.put("categoryId", String.valueOf(document.getCategoryId()));
                    }
                    SearchResult sr = new SearchResult(chunks.get(i).text(), meta);
                    bm25IndexService.addDocument(document.getId(), sr);
                }
                successCount++;
            } catch (Exception e) {
                log.error("[BM25 预热] [文档ID={}] 重建 BM25 索引失败: {}",
                        document.getId(), e.getMessage());
            }
        }

        log.info("[BM25 预热] 完成，成功载入文档数: {}/{}", successCount, completedDocuments.size());
    }

    private dev.langchain4j.data.document.DocumentParser parserFor(Path filePath) {
        String ext = getFileExtension(filePath);
        if ("pdf".equals(ext)) return new ApachePdfBoxDocumentParser();
        if (OFFICE_EXTENSIONS.contains(ext)) return new ApachePoiDocumentParser();
        throw new IllegalArgumentException("不支持的文件类型: " + ext);
    }

    private String getFileExtension(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex < 0 || dotIndex == fileName.length() - 1) ? "" : fileName.substring(dotIndex + 1);
    }
}
