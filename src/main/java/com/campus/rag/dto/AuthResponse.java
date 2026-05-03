package com.campus.rag.dto;

import com.campus.rag.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录或注册成功后的认证返回体。
 */
@Data
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private Long userId;
    private String username;
    private UserRole role;
}
