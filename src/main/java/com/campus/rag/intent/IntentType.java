package com.campus.rag.intent;

/**
 * 校园问答意图分类枚举。
 * 轻量三分类：知识问答 / 办事引导 / 闲聊，毕业设计场景足够。
 */
public enum IntentType {
    /** 知识库问答：走完整 RAG 管道检索知识库后生成回答 */
    KNOWLEDGE_QA,
    /** 校园办事引导：定向检索办事流程类文档，回答中注入步骤提示 */
    CAMPUS_GUIDE,
    /** 闲聊/无关问题：直接拒答，不浪费检索资源 */
    CHITCHAT
}
