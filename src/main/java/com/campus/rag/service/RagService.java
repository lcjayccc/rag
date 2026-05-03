package com.campus.rag.service;

import com.campus.rag.dto.RagPromptResult;

public interface RagService {
    /**
     * 检索知识库并构建增强 Prompt，同时返回召回统计和来源信息。
     *
     * @param userMessage 用户原始提问
     * @return Prompt、召回数量、相似度和来源文档
     */
    RagPromptResult buildRagPrompt(String userMessage);
}
