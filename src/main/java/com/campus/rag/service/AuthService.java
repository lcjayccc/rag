package com.campus.rag.service;

import com.campus.rag.dto.AuthResponse;

/**
 * 登录注册服务。
 */
public interface AuthService {

    AuthResponse login(String username, String password);

    AuthResponse register(String username, String password, String email);
}
