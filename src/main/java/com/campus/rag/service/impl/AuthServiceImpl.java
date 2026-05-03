package com.campus.rag.service.impl;

import com.campus.rag.common.exception.BusinessException;
import com.campus.rag.dto.AuthResponse;
import com.campus.rag.entity.User;
import com.campus.rag.entity.UserRole;
import com.campus.rag.mapper.UserMapper;
import com.campus.rag.service.AuthService;
import com.campus.rag.service.AuthTokenService;
import com.campus.rag.service.PasswordService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 最小登录注册实现。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordService passwordService;
    private final AuthTokenService tokenService;

    public AuthServiceImpl(UserMapper userMapper, PasswordService passwordService, AuthTokenService tokenService) {
        this.userMapper = userMapper;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
    }

    @Override
    public AuthResponse login(String username, String password) {
        User user = userMapper.selectByUsername(normalizeUsername(username));
        if (user == null || user.getStatus() == null || user.getStatus() != 1
                || !matchesOrUpgradeLegacyPassword(user, password)) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        user.setLastLogin(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return response(user);
    }

    private boolean matchesOrUpgradeLegacyPassword(User user, String rawPassword) {
        if (passwordService.matches(rawPassword, user.getPassword())) {
            return true;
        }
        // 兼容早期明文账号：首次登录成功后立即升级为 BCrypt 哈希。
        if (user.getPassword() != null && user.getPassword().equals(rawPassword)) {
            user.setPassword(passwordService.hash(rawPassword));
            return true;
        }
        return false;
    }

    @Override
    public AuthResponse register(String username, String password, String email) {
        String normalizedUsername = normalizeUsername(username);
        validatePassword(password);
        if (userMapper.selectByUsername(normalizedUsername) != null) {
            throw new BusinessException(409, "用户名已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordService.hash(password));
        user.setEmail(email);
        user.setRole(UserRole.USER);
        user.setStatus(1);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insert(user);
        return response(user);
    }

    private AuthResponse response(User user) {
        return new AuthResponse(tokenService.createToken(user), user.getId(), user.getUsername(), user.getRole());
    }

    private String normalizeUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new BusinessException(400, "用户名不能为空");
        }
        String normalized = username.trim();
        if (normalized.contains("|")) {
            throw new BusinessException(400, "用户名包含非法字符");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new BusinessException(400, "密码至少需要 6 位");
        }
    }
}
