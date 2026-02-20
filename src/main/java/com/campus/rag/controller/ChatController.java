package com.campus.rag.controller;

import com.campus.rag.common.Result;
import com.campus.rag.service.AiChatService;
import org.springframework.web.bind.annotation.*;

/**
 * 核心对话接口
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AiChatService aiChatService;

    public ChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping
    public Result<String> chat(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                               @RequestBody ChatRequest request) {
        if (userId == null) {
            userId = 1L; // 临时默认，后续从 token 解析
        }
        String reply = aiChatService.chat(userId, request.getMessage());
        return Result.success(reply);
    }

    @lombok.Data
    public static class ChatRequest {
        private String message;
    }
}
