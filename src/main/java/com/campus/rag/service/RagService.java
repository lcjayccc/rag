package com.campus.rag.service;

import com.campus.rag.dto.RagPromptResult;

public interface RagService {

    /**
     * 默认使用全库范围构建 RAG Prompt，兼容旧调用方。
     *
     * @param userMessage 用户原始提问
     * @return Prompt、召回数量、相似度和来源文档
     */
    default RagPromptResult buildRagPrompt(String userMessage) {
        return buildRagPrompt(userMessage, null);
    }

    /**
     * 检索知识库并构建增强 Prompt，同时返回召回统计和来源信息。
     *
     * @param userMessage 用户原始提问
     * @param categoryId  知识库分类 ID，为空表示全库检索
     * @return Prompt、召回数量、相似度和来源文档
     */
    RagPromptResult buildRagPrompt(String userMessage, Long categoryId);
}
