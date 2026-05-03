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

    /**
     * 基于历史对话上下文，将多轮追问改写为独立完整问题。
     * Rewrite 失败时回退到原始问题，不影响主链路。
     *
     * @param question 用户当前提问
     * @param history  会话历史文本（可为空字符串）
     * @return 改写后的完整问题
     */
    default String rewriteQuery(String question, String history) {
        if (history == null || history.isBlank()) {
            return question;
        }
        return question;
    }
}
