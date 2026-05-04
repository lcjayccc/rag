package com.campus.rag.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 基于 LLM 的意图分类器实现。
 * 使用同步 ChatLanguageModel（temperature=0.1）+ 专用 Prompt，
 * 输出 JSON 格式的意图分类结果。失败时回退到 KNOWLEDGE_QA。
 */
@Slf4j
@Service
public class IntentClassifierImpl implements IntentClassifier {

    private final ChatLanguageModel chatLanguageModel;
    private final String classifyPrompt;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public IntentClassifierImpl(ChatLanguageModel chatLanguageModel,
                                @Value("${rag.intent.enabled:true}") boolean enabled) {
        this.chatLanguageModel = chatLanguageModel;
        this.enabled = enabled;
        this.objectMapper = new ObjectMapper();
        // 从 classpath 加载意图分类 Prompt 模板
        this.classifyPrompt = loadPrompt();
    }

    @Override
    public IntentResult classify(String question) {
        if (!enabled) {
            return defaultResult(question);
        }
        try {
            // 填充 Prompt 模板并调用 LLM
            String prompt = classifyPrompt.replace("{{question}}", question);
            String response = chatLanguageModel.generate(prompt).trim();

            // 从 LLM 返回中提取 JSON 对象
            String json = extractJson(response);
            IntentResult result = objectMapper.readValue(json, IntentResult.class);

            // 校验并归一化
            if (result.getIntent() == null) {
                log.warn("意图分类返回 null，回退到 KNOWLEDGE_QA");
                return defaultResult(question);
            }
            if (result.getSubQuestions() == null || result.getSubQuestions().isEmpty()) {
                result.setSubQuestions(Collections.singletonList(question));
            }

            log.debug("意图分类: \"{}\" → {} (confidence={})", question, result.getIntent(), result.getConfidence());
            return result;
        } catch (Exception e) {
            log.warn("意图分类失败，回退到 KNOWLEDGE_QA: \"{}\"", question, e);
            return defaultResult(question);
        }
    }

    /**
     * 从 LLM 返回文本中提取第一个 JSON 对象。
     * LLM 可能在 JSON 前后附带 Markdown 代码块标记或说明文字。
     */
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    private IntentResult defaultResult(String question) {
        IntentResult result = new IntentResult();
        result.setIntent(IntentType.KNOWLEDGE_QA);
        result.setConfidence(1.0);
        result.setSubQuestions(Collections.singletonList(question));
        return result;
    }

    private String loadPrompt() {
        try (var stream = getClass().getClassLoader()
                .getResourceAsStream("prompts/intent-classify.st")) {
            if (stream == null) {
                log.error("未找到 prompts/intent-classify.st，意图分类将不可用");
                return "请将以下问题分类为 KNOWLEDGE_QA / CAMPUS_GUIDE / CHITCHAT，输出 JSON。问题：{{question}}";
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("加载意图分类 Prompt 失败", e);
            return "请将以下问题分类为 KNOWLEDGE_QA / CAMPUS_GUIDE / CHITCHAT，输出 JSON。问题：{{question}}";
        }
    }
}
