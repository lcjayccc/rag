package com.campus.rag.service.impl;

import com.campus.rag.auth.AuthPrincipal;
import com.campus.rag.common.exception.BusinessException;
import com.campus.rag.entity.User;
import com.campus.rag.entity.UserRole;
import com.campus.rag.service.AuthTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 基于 HMAC-SHA256 的轻量 Token 服务。
 */
@Service
public class HmacAuthTokenService implements AuthTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;
    private final long expireSeconds;

    public HmacAuthTokenService(@Value("${app.auth.token-secret}") String secret,
                                @Value("${app.auth.token-expire-seconds}") long expireSeconds) {
        this.secret = secret;
        this.expireSeconds = expireSeconds;
    }

    @Override
    public String createToken(User user) {
        long expireAt = Instant.now().getEpochSecond() + expireSeconds;
        String payload = user.getId() + "|" + user.getUsername() + "|" + user.getRole().name() + "|" + expireAt;
        String encodedPayload = base64Url(payload);
        return encodedPayload + "." + sign(encodedPayload);
    }

    @Override
    public AuthPrincipal parseToken(String token) {
        if (token == null || token.isBlank() || !token.contains(".")) {
            throw new BusinessException(401, "无效登录凭证");
        }
        String[] parts = token.split("\\.", 2);
        String expectedSign = sign(parts[0]);
        if (!constantTimeEquals(expectedSign, parts[1])) {
            throw new BusinessException(401, "无效登录凭证");
        }

        String payload;
        String[] values;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            values = payload.split("\\|", 4);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(401, "无效登录凭证");
        }

        if (values.length != 4) {
            throw new BusinessException(401, "无效登录凭证");
        }

        try {
            long expireAt = Long.parseLong(values[3]);
            if (Instant.now().getEpochSecond() > expireAt) {
                throw new BusinessException(401, "登录已过期，请重新登录");
            }
            return new AuthPrincipal(Long.valueOf(values[0]), values[1], UserRole.valueOf(values[2]));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(401, "无效登录凭证");
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Token 签名失败", e);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }
}
