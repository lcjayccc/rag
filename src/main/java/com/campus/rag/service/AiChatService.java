package com.campus.rag.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 聊天服务接口。
 */
public interface AiChatService {

    /**
     * 基础同步对话方法，主要用于接口调试。
     *
     * @param userMessage 用户原始问题
     * @return AI 生成的完整回答
     */
    String chatWithAi(String userMessage);

    /**
     * 默认使用全库范围进行流式问答，兼容旧调用方。
     *
     * @param userMessage 用户原始问题
     * @return SSE 推送通道
     */
    default SseEmitter streamChatWithAi(String userMessage) {
        return streamChatWithAi(userMessage, null);
    }

    /**
     * 流式问答方法，可按知识库分类限制 RAG 召回范围。
     *
     * @param userMessage 用户原始问题
     * @param categoryId  知识库分类 ID，为空表示全库检索
     * @return SSE 推送通道
     */
    SseEmitter streamChatWithAi(String userMessage, Long categoryId);
}
