package com.campus.rag.auth;

import com.campus.rag.common.exception.BusinessException;
import com.campus.rag.entity.UserRole;

/**
 * 基于 ThreadLocal 保存单次 HTTP 请求的登录用户。
 */
public final class AuthContext {

    private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthPrincipal principal) {
        HOLDER.set(principal);
    }

    public static AuthPrincipal requireLogin() {
        AuthPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BusinessException(401, "请先登录");
        }
        return principal;
    }

    public static AuthPrincipal requireAdmin() {
        AuthPrincipal principal = requireLogin();
        if (principal.getRole() != UserRole.ADMIN) {
            throw new BusinessException(403, "当前用户无权管理知识库");
        }
        return principal;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
