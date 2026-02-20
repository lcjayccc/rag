package com.campus.rag.controller;

import com.campus.rag.common.Result;
import org.springframework.web.bind.annotation.*;

/**
 * 登录注册
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public Result<?> login(@RequestBody LoginRequest request) {
        // TODO: 校验用户名密码，返回 token
        return Result.success();
    }

    @PostMapping("/register")
    public Result<?> register(@RequestBody RegisterRequest request) {
        // TODO: 注册新用户
        return Result.success();
    }

    @lombok.Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @lombok.Data
    public static class RegisterRequest {
        private String username;
        private String password;
        private String email;
    }
}
