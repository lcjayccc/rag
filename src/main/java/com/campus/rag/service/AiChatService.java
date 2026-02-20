package com.campus.rag.service;

/**
 * 组装 RAG 链路的逻辑（检索增强生成）
 */
public interface AiChatService {

    /**
     * 基于用户提问进行 RAG 对话
     *
     * @param userId  用户 ID
     * @param message 用户消息
     * @return 模型回复
     */
    String chat(Long userId, String message);
}
