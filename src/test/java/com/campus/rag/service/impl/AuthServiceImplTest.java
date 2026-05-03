package com.campus.rag.service.impl;

import com.campus.rag.common.exception.BusinessException;
import com.campus.rag.dto.AuthResponse;
import com.campus.rag.entity.User;
import com.campus.rag.entity.UserRole;
import com.campus.rag.mapper.UserMapper;
import com.campus.rag.service.AuthTokenService;
import com.campus.rag.service.PasswordService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceImplTest {

    @Test
    void registerCreatesUserWithHashedPasswordAndDefaultUserRole() {
        RecordingUserMapper mapper = new RecordingUserMapper();
        PasswordService passwordService = new PasswordService();
        AuthServiceImpl service = new AuthServiceImpl(mapper, passwordService, new FixedTokenService());

        AuthResponse response = service.register("student", "plain-password", "student@example.com");

        User saved = mapper.insertedUsers.getFirst();
        assertEquals("student", saved.getUsername());
        assertEquals("student@example.com", saved.getEmail());
        assertEquals(UserRole.USER, saved.getRole());
        assertEquals(1, saved.getStatus());
        assertNotEquals("plain-password", saved.getPassword());
        assertTrue(passwordService.matches("plain-password", saved.getPassword()));
        assertEquals("fixed-token", response.getToken());
        assertEquals(UserRole.USER, response.getRole());
    }

    @Test
    void loginRejectsWrongPassword() {
        RecordingUserMapper mapper = new RecordingUserMapper();
        PasswordService passwordService = new PasswordService();
        User user = user(1L, "student", passwordService.hash("right-password"), UserRole.USER);
        mapper.existingUser = user;
        AuthServiceImpl service = new AuthServiceImpl(mapper, passwordService, new FixedTokenService());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login("student", "wrong-password"));

        assertEquals(401, error.getCode());
        assertEquals("用户名或密码错误", error.getMessage());
    }

    @Test
    void loginUpgradesLegacyPlainPasswordToBcryptHash() {
        RecordingUserMapper mapper = new RecordingUserMapper();
        PasswordService passwordService = new PasswordService();
        User user = user(1L, "admin", "admin123", UserRole.ADMIN);
        mapper.existingUser = user;
        AuthServiceImpl service = new AuthServiceImpl(mapper, passwordService, new FixedTokenService());

        AuthResponse response = service.login("admin", "admin123");

        assertEquals("fixed-token", response.getToken());
        assertEquals(UserRole.ADMIN, response.getRole());
        assertNotEquals("admin123", mapper.updatedUser.getPassword());
        assertTrue(passwordService.matches("admin123", mapper.updatedUser.getPassword()));
    }

    private static User user(Long id, String username, String password, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(username + "@example.com");
        user.setRole(role);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return user;
    }

    private static class FixedTokenService implements AuthTokenService {
        @Override
        public String createToken(User user) {
            return "fixed-token";
        }

        @Override
        public com.campus.rag.auth.AuthPrincipal parseToken(String token) {
            throw new UnsupportedOperationException("本测试不需要解析 Token");
        }
    }

    private static class RecordingUserMapper implements UserMapper {
        private final List<User> insertedUsers = new ArrayList<>();
        private User existingUser;
        private User updatedUser;

        @Override
        public User selectById(Long id) {
            return existingUser != null && existingUser.getId().equals(id) ? existingUser : null;
        }

        @Override
        public User selectByUsername(String username) {
            return existingUser != null && existingUser.getUsername().equals(username) ? existingUser : null;
        }

        @Override
        public int insert(User user) {
            user.setId(10L);
            insertedUsers.add(user);
            return 1;
        }

        @Override
        public int updateById(User user) {
            existingUser = user;
            updatedUser = user;
            return 1;
        }
    }
}
