package com.campus.rag.dto;

import com.campus.rag.entity.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

/**
 * 文档摄入管道数据载体（借鉴 ragent IngestionContext 模式）
 *
 * <p>贯穿 落盘 → 解析 → 切片 → 向量化 整条管道，
 * 避免在各步骤间传递大量离散参数。
 */
@Data
public class DocumentIngestionContext {

    /** 原始上传文件（用于获取文件名、类型等元信息） */
    private MultipartFile rawFile;

    /** 文件落盘后的绝对路径 */
    private Path savedFilePath;

    /** MySQL 数据库文档实体（与 DB 状态同步） */
    private Document dbDocument;

    /** LangChain4j 解析后的文档对象（包含原始文本 + 初始元数据） */
    private dev.langchain4j.data.document.Document parsedDocument;

    /** 文本切片结果，每个 TextSegment 已注入 documentId/fileName/chunkIndex 元数据 */
    private List<TextSegment> chunks;

    /** 向量化结果，与 chunks 列表一一对应 */
    private List<Embedding> embeddings;
}
