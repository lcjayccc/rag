package com.campus.rag.controller;

import com.campus.rag.auth.AuthContext;
import com.campus.rag.service.AiChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 新增引入 MediaType 和 SseEmitter
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AiChatService aiChatService;

    // 构造器注入 Service
    public ChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /**
     * 同步测试接口：GET /api/chat/simple?message=你的问题
     * * @param message 用户的提问文本
     * @return 纯文本响应结果
     */
    @GetMapping("/simple")
    public String simpleChat(@RequestParam String message) {
        AuthContext.requireLogin();
        // 调用 Service 层向大模型提问，并返回结果给调用方
        return aiChatService.chatWithAi(message);
    }

    // 流式问答接口：categoryId 为空时全库检索，不为空时只检索指定分类。
    // sessionId 用于多轮对话 Query Rewrite，为空时按单轮处理。
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter streamChat(@RequestParam String message,
                                 @RequestParam(required = false) Long categoryId,
                                 @RequestParam(required = false) String sessionId) {
        return aiChatService.streamChatWithAi(message, categoryId, sessionId);
    }
}
