package com.campus.rag.chunking;

import com.campus.rag.entity.DocumentCategory;
import com.campus.rag.mapper.DocumentCategoryMapper;
import org.springframework.stereotype.Component;

/**
 * 切片策略工厂：根据分类的 {@code defaultChunkingStrategy} 字段选择策略。
 *
 * <p>未设置策略或分类不存在时默认使用固定大小切片。
 */
@Component
public class ChunkingStrategyFactory {

    private final FixedSizeChunker fixedSizeChunker;
    private final StructureAwareChunker structureAwareChunker;
    private final SemanticChunker semanticChunker;
    private final DocumentCategoryMapper categoryMapper;

    public ChunkingStrategyFactory(FixedSizeChunker fixedSizeChunker,
                                   StructureAwareChunker structureAwareChunker,
                                   SemanticChunker semanticChunker,
                                   DocumentCategoryMapper categoryMapper) {
        this.fixedSizeChunker = fixedSizeChunker;
        this.structureAwareChunker = structureAwareChunker;
        this.semanticChunker = semanticChunker;
        this.categoryMapper = categoryMapper;
    }

    /**
     * 按分类 ID 选择切片策略。
     */
    public ChunkingStrategy select(Long categoryId) {
        if (categoryId == null) {
            return fixedSizeChunker;
        }
        DocumentCategory category = categoryMapper.selectById(categoryId);
        return select(category);
    }

    /**
     * 按分类实体选择切片策略。
     */
    public ChunkingStrategy select(DocumentCategory category) {
        if (category == null || category.getDefaultChunkingStrategy() == null) {
            return fixedSizeChunker;
        }
        return switch (category.getDefaultChunkingStrategy().toUpperCase()) {
            case "STRUCTURE_AWARE" -> structureAwareChunker;
            case "SEMANTIC" -> semanticChunker;
            default -> fixedSizeChunker;
        };
    }
}
