package com.campus.rag.service;

import com.campus.rag.entity.Document;
import com.campus.rag.mapper.DocumentMapper;
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

@Slf4j
@Service
@ConditionalOnProperty(name = "rag.vector.store", havingValue = "inmemory", matchIfMissing = true)
public class KnowledgeWarmupService {

    private final DocumentMapper documentMapper;
    private final DocumentIndexingService documentIndexingService;

    public KnowledgeWarmupService(DocumentMapper documentMapper,
                                  DocumentIndexingService documentIndexingService) {
        this.documentMapper = documentMapper;
        this.documentIndexingService = documentIndexingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        List<Document> completedDocuments = documentMapper.selectCompleted();
        log.info("[知识库预热] 开始重建内存向量库，待处理文档数: {}", completedDocuments.size());

        int successCount = 0;
        for (Document document : completedDocuments) {
            if (!StringUtils.hasText(document.getFilePath())) {
                log.warn("[知识库预热] [文档ID={}] 跳过：filePath 为空", document.getId());
                continue;
            }

            Path filePath = Paths.get(document.getFilePath());
            if (!Files.exists(filePath)) {
                log.warn("[知识库预热] [文档ID={}] 跳过：文件不存在 {}", document.getId(), filePath);
                continue;
            }

            try {
                documentIndexingService.index(document, filePath);
                successCount++;
            } catch (Exception e) {
                log.error("[知识库预热] [文档ID={}] 重建索引失败，继续处理后续文档: {}",
                        document.getId(), e.getMessage(), e);
            }
        }

        log.info("[知识库预热] 完成，成功载入文档数: {}/{}", successCount, completedDocuments.size());
    }
}
