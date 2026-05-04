package com.campus.rag.chunking;

import com.campus.rag.entity.Document;
import dev.langchain4j.data.document.Metadata;

/**
 * 切片器公共逻辑：构建统一的 Chunk 元数据。
 */
public abstract class BaseChunker {

    /**
     * 构建切片元数据，含基础字段和增强字段。
     *
     * @param dbDoc        数据库文档记录
     * @param chunkIndex   切片序号（从 0 开始）
     * @param totalChunks  切片总数（用于估算页码）
     * @param sectionTitle 章节标题（结构感知切片时提取，其他策略传 null）
     */
    protected Metadata buildMetadata(Document dbDoc, int chunkIndex, int totalChunks,
                                     String sectionTitle) {
        Metadata meta = new Metadata();
        meta.put("documentId", String.valueOf(dbDoc.getId()));
        meta.put("fileName", dbDoc.getFileName());
        meta.put("chunkIndex", String.valueOf(chunkIndex));
        if (dbDoc.getCategoryId() != null) {
            meta.put("categoryId", String.valueOf(dbDoc.getCategoryId()));
        }

        // 增强元数据：文档标题（从文件名推导，去除扩展名）
        String documentTitle = deriveDocumentTitle(dbDoc.getFileName());
        meta.put("documentTitle", documentTitle);

        // 增强元数据：页码估算（chunkIndex / totalChunks 比例映射到假设的页数）
        int estimatedPage = totalChunks > 1 ? (chunkIndex * 10 / totalChunks) + 1 : 1;
        meta.put("pageNumber", String.valueOf(estimatedPage));

        // 增强元数据：章节标题（仅结构感知切片器设置）
        if (sectionTitle != null && !sectionTitle.isEmpty()) {
            meta.put("sectionTitle", sectionTitle);
        }

        return meta;
    }

    private String deriveDocumentTitle(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "未命名文档";
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }
}
