package com.campus.rag.service;
//Spring MVC 框架中用于实现服务器发送事件（Server-Sent Events，SSE） 的核心类。
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
/**
 * AI 聊天服务接口
 */
public interface AiChatService {

    /**
     * 基础对话方法
     * @param userMessage 用户的提问文本
     * @return AI 生成的完整回答
     */
    String chatWithAi(String userMessage);

    /**
     * 新增：流式对话方法 (打字机效果)
     * @param userMessage 用户的提问文本
     * @return SseEmitter 对象，用于持续向前端推送数据
     */
    SseEmitter streamChatWithAi(String userMessage);

}
