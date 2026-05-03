package com.campus.rag.controller;

import com.campus.rag.service.AiChatService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatControllerCategoryScopeTest {

    @Test
    void streamChatPassesCategoryIdToService() {
        RecordingAiChatService service = new RecordingAiChatService();
        ChatController controller = new ChatController(service);

        controller.streamChat("校级三好学生申请条件", 7L, "test-session-001");

        assertEquals("校级三好学生申请条件", service.recordedMessage);
        assertEquals(7L, service.recordedCategoryId);
        assertEquals("test-session-001", service.recordedSessionId);
    }

    private static class RecordingAiChatService implements AiChatService {
        private String recordedMessage;
        private Long recordedCategoryId;
        private String recordedSessionId;

        @Override
        public String chatWithAi(String userMessage) {
            return "ok";
        }

        @Override
        public SseEmitter streamChatWithAi(String userMessage, Long categoryId, String sessionId) {
            recordedMessage = userMessage;
            recordedCategoryId = categoryId;
            recordedSessionId = sessionId;
            return new SseEmitter();
        }
    }
}
