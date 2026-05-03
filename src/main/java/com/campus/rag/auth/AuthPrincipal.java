package com.campus.rag.auth;

import com.campus.rag.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 当前请求中的登录用户信息。
 */
@Getter
@AllArgsConstructor
public class AuthPrincipal {

    private final Long userId;
    private final String username;
    private final UserRole role;
}
