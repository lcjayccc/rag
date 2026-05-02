package com.campus.rag.service.impl;

import com.campus.rag.entity.Document;
import com.campus.rag.service.DocumentIndexingService;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
@Service
public class DocumentIndexingServiceImpl implements DocumentIndexingService {

    private static final Set<String> OFFICE_EXTENSIONS = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public DocumentIndexingServiceImpl(EmbeddingModel embeddingModel,
                                       EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    @Override
    public void index(Document document, Path filePath) {
        dev.langchain4j.data.document.Document lcDoc = parseDocument(filePath);
        log.info("[文档索引] [文档ID={}] 文件解析完成，类型: {}，原始文本长度: {} 字符",
                document.getId(), document.getFileType(), lcDoc.text().length());

        List<TextSegment> chunks = splitAndEnrich(lcDoc, document);
        log.info("[文档索引] [文档ID={}] 切片完成，共 {} 个 Chunk（策略: recursive 500/50）",
                document.getId(), chunks.size());

        List<Embedding> embeddings = embeddingModel.embedAll(chunks).content();
        embeddingStore.addAll(embeddings, chunks);
        log.info("[文档索引] [文档ID={}] 向量化完成，向量维度: {}，已写入 EmbeddingStore",
                document.getId(), embeddings.isEmpty() ? 0 : embeddings.get(0).vector().length);
    }

    @Override
    public void removeByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }

        embeddingStore.removeAll(metadataKey("documentId").isEqualTo(String.valueOf(documentId)));
        log.info("[文档索引] [文档ID={}] 已从 EmbeddingStore 移除对应切片", documentId);
    }

    private dev.langchain4j.data.document.Document parseDocument(Path filePath) {
        return FileSystemDocumentLoader.loadDocument(filePath, parserFor(filePath));
    }

    private DocumentParser parserFor(Path filePath) {
        String extension = getFileExtension(filePath);
        if ("pdf".equals(extension)) {
            return new ApachePdfBoxDocumentParser();
        }
        if (OFFICE_EXTENSIONS.contains(extension)) {
            return new ApachePoiDocumentParser();
        }
        throw new IllegalArgumentException("不支持的文件类型: " + extension);
    }

    private String getFileExtension(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }

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
}
