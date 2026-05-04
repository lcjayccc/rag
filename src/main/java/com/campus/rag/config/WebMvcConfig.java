package com.campus.rag.config;

import com.campus.rag.interceptor.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层拦截器配置。
 * 注册顺序：AuthInterceptor → RateLimitInterceptor（先鉴权后限流）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor,
                        RateLimitInterceptor rateLimitInterceptor) {
        this.authInterceptor = authInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 鉴权拦截器：除登录注册外所有 /api/** 都需要登录
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
        // 限流拦截器：只限制问答 SSE 接口，不限流文档管理
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/chat/**");
    }
}
