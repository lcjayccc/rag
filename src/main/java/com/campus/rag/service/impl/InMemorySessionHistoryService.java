package com.campus.rag.service.impl;

import com.campus.rag.service.SessionHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的会话历史管理（Redis 不可用时的回退实现）。
 * 服务重启后历史自动清空，不影响核心问答功能。
 */
@Slf4j
@Service
@ConditionalOnMissingBean(name = "redisSessionHistoryService")
public class InMemorySessionHistoryService implements SessionHistoryService {

    /** 每会话保留最近 3 轮问答（6 条消息） */
    private static final int MAX_TURNS = 3;
    /** 每条回答截取前 200 字符 */
    private static final int ANSWER_TRUNCATE = 200;

    private final ConcurrentHashMap<String, List<String>> sessions = new ConcurrentHashMap<>();

    @Override
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

    @Override
    public void addTurn(String sessionId, String question, String answer) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        List<String> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add("用户问：" + question);
        history.add("助手答：" + truncate(answer, ANSWER_TRUNCATE));
        // 保持最近 3 轮（6 条）
        while (history.size() > MAX_TURNS * 2) {
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
