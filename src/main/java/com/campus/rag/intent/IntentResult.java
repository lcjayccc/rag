package com.campus.rag.intent;

import lombok.Data;

import java.util.List;

/**
 * 意图分类结果。
 * 包含意图类型、置信度，以及问题拆分后的子问题列表（仅多子问题时非空）。
 */
@Data
public class IntentResult {
    /** 分类后的意图类型 */
    private IntentType intent;
    /** 分类置信度 0.0~1.0，由 LLM 返回 */
    private Double confidence;
    /** 子问题拆分结果：用户提问含多个子问题时非空，否则为单元素列表 */
    private List<String> subQuestions;
}
