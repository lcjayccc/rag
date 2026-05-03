package com.campus.rag.service;

import com.campus.rag.auth.AuthPrincipal;
import com.campus.rag.entity.User;

/**
 * 登录 Token 的生成与解析。
 */
public interface AuthTokenService {

    String createToken(User user);

    AuthPrincipal parseToken(String token);
}
