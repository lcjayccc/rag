package com.campus.rag.service.impl;

import com.campus.rag.chunking.ChunkingStrategy;
import com.campus.rag.chunking.ChunkingStrategyFactory;
import com.campus.rag.entity.Document;
import com.campus.rag.parser.DocumentParserSelector;
import com.campus.rag.search.Bm25IndexService;
import com.campus.rag.search.SearchResult;
import com.campus.rag.service.DocumentIndexingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 文档索引服务实现。
 *
 * <p>Phase 4 重构：解析器选择 → 多策略切片 → 向量化 → 写入存储。
 * 解析失败时自动回退 Tika，切片策略按文档分类选择。
 */
@Slf4j
@Service
public class DocumentIndexingServiceImpl implements DocumentIndexingService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final Bm25IndexService bm25IndexService;
    private final DocumentParserSelector parserSelector;
    private final ChunkingStrategyFactory chunkingFactory;

    public DocumentIndexingServiceImpl(EmbeddingModel embeddingModel,
                                       EmbeddingStore<TextSegment> embeddingStore,
                                       Bm25IndexService bm25IndexService,
                                       DocumentParserSelector parserSelector,
                                       ChunkingStrategyFactory chunkingFactory) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.bm25IndexService = bm25IndexService;
        this.parserSelector = parserSelector;
        this.chunkingFactory = chunkingFactory;
    }

    @Override
    public void index(Document document, Path filePath) {
        // Phase 4: 使用策略选择器（主解析器 → Tika 回退）
        dev.langchain4j.data.document.Document lcDoc = parserSelector.parse(filePath);
        log.info("[文档索引] [文档ID={}] 文件解析完成，类型: {}，原始文本长度: {} 字符",
                document.getId(), document.getFileType(), lcDoc.text().length());

        // Phase 4: 按分类选择切片策略
        ChunkingStrategy strategy = chunkingFactory.select(document.getCategoryId());
        String strategyName = strategy.getClass().getSimpleName();
        List<TextSegment> chunks = strategy.chunk(lcDoc.text(), document);
        log.info("[文档索引] [文档ID={}] 切片完成，共 {} 个 Chunk（策略: {}）",
                document.getId(), chunks.size(), strategyName);

        List<Embedding> embeddings = embeddingModel.embedAll(chunks).content();
        embeddingStore.addAll(embeddings, chunks);
        log.info("[文档索引] [文档ID={}] 向量化完成，向量维度: {}，已写入 EmbeddingStore",
                document.getId(), embeddings.isEmpty() ? 0 : embeddings.get(0).vector().length);

        // 同步 BM25 索引
        for (TextSegment chunk : chunks) {
            SearchResult sr = new SearchResult(chunk.text(), chunk.metadata());
            bm25IndexService.addDocument(document.getId(), sr);
        }
        log.info("[文档索引] [文档ID={}] BM25 索引已同步，{} 个 Chunk", document.getId(), chunks.size());
    }

    @Override
    public void removeByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }
        embeddingStore.removeAll(metadataKey("documentId").isEqualTo(String.valueOf(documentId)));
        bm25IndexService.removeDocument(documentId);
        log.info("[文档索引] [文档ID={}] 已从 EmbeddingStore 和 BM25 移除对应切片", documentId);
    }
}
