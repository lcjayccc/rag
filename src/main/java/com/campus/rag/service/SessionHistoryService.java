package com.campus.rag.service;

/**
 * 会话历史管理接口。
 * 用于多轮对话 Query Rewrite，记录最近 N 轮问答对。
 *
 * <p>Phase 3：从具体类重构为接口，支持内存（InMemory）和 Redis 两种实现。
 * Redis 不可用时自动回退到 InMemory 实现。
 */
public interface SessionHistoryService {

    /**
     * 获取会话的最近若干轮对话历史，格式为可拼入 Prompt 的文本。
     *
     * @param sessionId 会话标识（前端生成 UUID）
     * @return 历史文本，无历史时返回空字符串
     */
    String getHistory(String sessionId);

    /**
     * 向会话追加一轮问答。
     *
     * @param sessionId 会话标识
     * @param question  用户提问
     * @param answer    助手回答（只截取前 200 字，控制 Prompt 长度）
     */
    void addTurn(String sessionId, String question, String answer);
}
