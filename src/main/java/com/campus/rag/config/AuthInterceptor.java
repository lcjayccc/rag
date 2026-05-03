package com.campus.rag.config;

import com.campus.rag.auth.AuthContext;
import com.campus.rag.service.AuthTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 解析认证 Token，并把登录用户放入 AuthContext。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthTokenService tokenService;

    public AuthInterceptor(AuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        AuthContext.set(tokenService.parseToken(resolveToken(request)));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length());
        }
        // EventSource 无法自定义请求头，流式聊天接口通过 query token 兼容登录态。
        return request.getParameter("token");
    }
}
