package com.campus.rag.controller;

import com.campus.rag.common.Result;
import com.campus.rag.dto.AuthResponse;
import com.campus.rag.service.AuthService;
import org.springframework.web.bind.annotation.*;

/**
 * 登录注册
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@RequestBody LoginRequest request) {
        return Result.success(authService.login(request.getUsername(), request.getPassword()));
    }

    @PostMapping("/register")
    public Result<AuthResponse> register(@RequestBody RegisterRequest request) {
        return Result.success(authService.register(request.getUsername(), request.getPassword(), request.getEmail()));
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
