package com.campus.rag.service.impl;

import com.campus.rag.service.AiChatService;
import org.springframework.stereotype.Service;

/**
 * RAG 对话服务实现（后续接入 LangChain4j 等）
 */
@Service
public class AiChatServiceImpl implements AiChatService {

    @Override
    public String chat(Long userId, String message) {
        // TODO: 组装 RAG 链路，检索 + 大模型生成
        return "RAG 回复（待实现）: " + message;
    }
}
