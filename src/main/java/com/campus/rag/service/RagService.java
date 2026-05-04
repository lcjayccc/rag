package com.campus.rag.service;

import com.campus.rag.dto.RagPromptResult;
import com.campus.rag.intent.IntentType;

import java.util.Collections;
import java.util.List;

public interface RagService {

    /**
     * 默认使用全库范围构建 RAG Prompt，兼容旧调用方。
     *
     * @param userMessage 用户原始提问
     * @return Prompt、召回数量、相似度和来源文档
     */
    default RagPromptResult buildRagPrompt(String userMessage) {
        return buildRagPrompt(userMessage, null);
    }

    /**
     * 检索知识库并构建增强 Prompt，同时返回召回统计和来源信息。
     *
     * @param userMessage 用户原始提问
     * @param categoryId  知识库分类 ID，为空表示全库检索
     * @return Prompt、召回数量、相似度和来源文档
     */
    RagPromptResult buildRagPrompt(String userMessage, Long categoryId);

    /**
     * 基于历史对话上下文，将多轮追问改写为独立完整问题。
     * Rewrite 失败时回退到原始问题，不影响主链路。
     *
     * @param question 用户当前提问
     * @param history  会话历史文本（可为空字符串）
     * @return 改写后的完整问题
     */
    String rewriteQuery(String question, String history);

    /**
     * 基于历史对话上下文改写问题，并判断是否需要拆分为多个子问题。
     * 如果意图分类已识别子问题，则跳过改写只做拆分；否则走完整改写流程。
     *
     * @param question    用户当前提问
     * @param history     会话历史文本
     * @param subQuestions 意图分类已拆分的子问题（null 或空表示不走拆分）
     * @return 改写/拆分后的检索问题列表
     */
    default List<String> rewriteAndSplit(String question, String history, List<String> subQuestions) {
        // 默认实现：意图分类已拆分则直接使用，否则走单问题改写
        if (subQuestions != null && subQuestions.size() > 1) {
            return subQuestions;
        }
        return Collections.singletonList(rewriteQuery(question, history));
    }

    /**
     * Phase 3: 支持多子问题检索的 RAG Prompt 构建。
     * 每个子问题独立检索后合并上下文，根据意图类型注入不同的 Prompt 引导语。
     *
     * @param searchQueries 改写/拆分后的检索问题列表
     * @param categoryId    知识库分类 ID，为空表示全库检索
     * @param intentType    意图类型（用于注入办事引导语等）
     * @return 合并后的 Prompt、召回统计和来源信息
     */
    RagPromptResult buildRagPrompt(List<String> searchQueries, Long categoryId, IntentType intentType);
}
