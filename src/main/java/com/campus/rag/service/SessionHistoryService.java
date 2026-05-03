package com.campus.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的会话历史管理，用于 Query Rewrite。
 *
 * <p>每个会话保留最近 3 轮对话，Rewrite 时作为上下文注入。
 * 服务重启后历史自动清空，不影响核心问答功能。
 */
@Slf4j
@Service
public class SessionHistoryService {

    private final ConcurrentHashMap<String, List<String>> sessions = new ConcurrentHashMap<>();

    public String getHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "";
        }
        List<String> history = sessions.get(sessionId);
        if (history == null || history.isEmpty()) {
            return "";
        }
        return String.join("\n", history);
    }

    public void addTurn(String sessionId, String question, String answer) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        List<String> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add("用户问：" + question);
        history.add("助手答：" + truncate(answer, 200));
        while (history.size() > 6) {
            history.removeFirst();
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "…";
    }
}
