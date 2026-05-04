package com.campus.rag.interceptor;

import com.campus.rag.auth.AuthContext;
import com.campus.rag.auth.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * 用户级限流拦截器。
 * 使用 Redis INCR + EXPIRE 实现每分钟请求上限，Redis 不可用时放行。
 *
 * <p>拦截路径：/api/chat/stream（只限流问答接口，不限流文档管理）。
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "rate:";

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final int maxPerMinute;
    private final int ttlSeconds;

    public RateLimitInterceptor(StringRedisTemplate redisTemplate,
                                @Value("${rag.rate-limit.enabled:true}") boolean enabled,
                                @Value("${rag.rate-limit.max-per-minute:10}") int maxPerMinute,
                                @Value("${rag.rate-limit.ttl-seconds:60}") int ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.maxPerMinute = maxPerMinute;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!enabled) {
            return true;
        }

        AuthPrincipal principal;
        try {
            principal = AuthContext.requireLogin();
        } catch (Exception e) {
            // 未登录用户由 AuthInterceptor 处理，此处放行
            return true;
        }

        Long userId = principal.getUserId();
        String key = KEY_PREFIX + userId;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            // 首次访问设置 TTL
            if (count != null && count == 1) {
                redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            }
            if (count != null && count > maxPerMinute) {
                response.setStatus(429);
                response.setContentType("text/plain; charset=UTF-8");
                response.getWriter().write("请求过于频繁，请稍后重试（每分钟最多 " + maxPerMinute + " 次）");
                log.warn("用户 {} 触发限流，当前计数 {}", userId, count);
                return false;
            }
        } catch (Exception e) {
            // Redis 不可用时放行，不阻塞正常问答
            log.warn("限流检查 Redis 异常，放行: {}", e.getMessage());
        }
        return true;
    }
}
