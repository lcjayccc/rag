package com.campus.rag.chunking;

import com.campus.rag.entity.Document;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/**
 * 文档切片策略接口。
 *
 * <p>不同策略适用于不同文档结构：
 * <ul>
 *   <li>{@code FIXED_SIZE}：通用，固定大小 + 重叠</li>
 *   <li>{@code STRUCTURE_AWARE}：有标题层级文档，按标题边界切分</li>
 *   <li>{@code SEMANTIC}：FAQ/短文档，按段落和问答对边界切分</li>
 * </ul>
 */
public interface ChunkingStrategy {

    /**
     * 将文档文本切分为带元数据的文本段。
     *
     * @param documentText 解析后的文档全文
     * @param dbDoc        数据库中的文档记录
     * @return 带元数据的文本段列表
     */
    List<TextSegment> chunk(String documentText, Document dbDoc);
}
