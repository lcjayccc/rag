package com.campus.rag.service.impl;

import com.campus.rag.auth.AuthPrincipal;
import com.campus.rag.common.exception.BusinessException;
import com.campus.rag.entity.User;
import com.campus.rag.entity.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HmacAuthTokenServiceTest {

    @Test
    void createdTokenCanBeParsedIntoPrincipal() {
        HmacAuthTokenService service = new HmacAuthTokenService("test-secret", 3600);
        User user = new User();
        user.setId(7L);
        user.setUsername("admin");
        user.setRole(UserRole.ADMIN);

        String token = service.createToken(user);
        AuthPrincipal principal = service.parseToken(token);

        assertEquals(7L, principal.getUserId());
        assertEquals("admin", principal.getUsername());
        assertEquals(UserRole.ADMIN, principal.getRole());
    }

    @Test
    void tamperedTokenIsRejected() {
        HmacAuthTokenService service = new HmacAuthTokenService("test-secret", 3600);
        User user = new User();
        user.setId(7L);
        user.setUsername("admin");
        user.setRole(UserRole.ADMIN);

        String token = service.createToken(user) + "tampered";

        BusinessException error = assertThrows(BusinessException.class, () -> service.parseToken(token));
        assertEquals(401, error.getCode());
    }
}
