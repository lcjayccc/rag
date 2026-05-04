package com.campus.rag.intent;

/**
 * 意图分类器接口。
 * 实现类负责根据用户问题判断意图类型，失败时回退到 KNOWLEDGE_QA。
 */
public interface IntentClassifier {
    /**
     * 对用户问题进行意图分类。
     *
     * @param question 用户原始提问
     * @return 分类结果（含意图类型、置信度和子问题列表）
     */
    IntentResult classify(String question);
}
