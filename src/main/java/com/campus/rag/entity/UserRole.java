package com.campus.rag.entity;

/**
 * 系统最小角色集合。
 */
public enum UserRole {
    /**
     * 管理员：可以维护知识库。
     */
    ADMIN,

    /**
     * 普通用户：只允许使用智能问答。
     */
    USER
}
