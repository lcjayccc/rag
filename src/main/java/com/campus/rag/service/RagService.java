package com.campus.rag.service;

public interface RagService {
    /**
     * 检索知识库并构建增强超级 Prompt
     * @param userMessage 用户原始提问
     * @return 注入了上下文知识的完整 Prompt
     */
    String buildRagPrompt(String userMessage);
}
