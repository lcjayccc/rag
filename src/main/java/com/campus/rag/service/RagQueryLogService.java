package com.campus.rag.service;

import com.campus.rag.dto.RagPromptResult;

public interface RagQueryLogService {

    /**
     * 记录一次 RAG 问答检索结果。
     *
     * @param userId 用户 ID
     * @param question 原始问题
     * @param result RAG 检索结构化结果
     * @param latencyMs 从接收问题到回答完成的总耗时
     */
    void record(Long userId, String question, RagPromptResult result, long latencyMs);
}
