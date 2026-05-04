package com.campus.rag.service.impl;

import com.campus.rag.service.SessionHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的会话历史管理。
 * 使用 LIST 存储最近对话，TTL 2 小时自动过期，服务重启数据不丢失。
 *
 * <p>Key 格式：session:{sessionId}:history
 * <p>Redis 不可用时 Spring 不会注入此 Bean，自动回退到 InMemorySessionHistoryService。
 */
@Slf4j
@Service("redisSessionHistoryService")
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisSessionHistoryService implements SessionHistoryService {

    private static final String KEY_PREFIX = "session:";
    private static final String KEY_SUFFIX = ":history";
    private static final int MAX_TURNS = 3;
    private static final int ANSWER_TRUNCATE = 200;
    /** 会话历史 TTL：2 小时 */
    private static final long TTL_HOURS = 2;

    private final StringRedisTemplate redisTemplate;

    public RedisSessionHistoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "";
        }
        try {
            String key = buildKey(sessionId);
            List<String> history = redisTemplate.opsForList().range(key, 0, -1);
            if (history == null || history.isEmpty()) {
                return "";
            }
            return String.join("\n", history);
        } catch (Exception e) {
            log.warn("Redis 读取会话历史失败，返回空: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public void addTurn(String sessionId, String question, String answer) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            String key = buildKey(sessionId);
            // RPUSH 追加两条消息
            redisTemplate.opsForList().rightPushAll(key,
                    "用户问：" + question,
                    "助手答：" + truncate(answer, ANSWER_TRUNCATE));
            // LTRIM 保留最近 3 轮（6 条）
            redisTemplate.opsForList().trim(key, -(MAX_TURNS * 2L), -1);
            // 刷新 TTL
            redisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis 写入会话历史失败: {}", e.getMessage());
        }
    }

    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId + KEY_SUFFIX;
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "…";
    }
}
