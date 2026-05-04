package com.campus.rag.service.impl;

import com.campus.rag.dto.RagPromptResult;
import com.campus.rag.dto.RagSource;
import com.campus.rag.intent.IntentType;
import com.campus.rag.search.HybridSearchService;
import com.campus.rag.search.SearchResult;
import com.campus.rag.service.RagService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagServiceImpl implements RagService {

    private static final int TOP_K = 3;

    private final HybridSearchService hybridSearchService;
    private final ChatLanguageModel chatLanguageModel;

    @Value("${rag.retrieval.min-score:0.65}")
    private double minScore;

    @Value("classpath:prompts/answer-chat-kb.st")
    private Resource promptTemplateResource;

    @Value("classpath:prompts/query-rewrite.st")
    private Resource rewritePromptResource;

    public RagServiceImpl(HybridSearchService hybridSearchService,
                          ChatLanguageModel chatLanguageModel) {
        this.hybridSearchService = hybridSearchService;
        this.chatLanguageModel = chatLanguageModel;
    }

    @Override
    public RagPromptResult buildRagPrompt(String userMessage, Long categoryId) {
        log.info("【RAG 检索大脑】开始为问题寻找答案: \"{}\"", userMessage);
        long startTime = System.currentTimeMillis();

        try {
            // 1. 混合检索（向量 + BM25 → RRF → Rerank）
            List<SearchResult> results = hybridSearchService.search(userMessage, categoryId, TOP_K, minScore);

            log.info("【RAG 检索大脑】命中强相关知识碎片数量: {}", results.size());

            // 2. 提取碎片文本并拼接，注入段落来源标签。
            String context = buildContextWithCitations(results);

            if (context.isEmpty()) {
                context = "暂无相关校园文档知识。";
            }

            // 3. 组装终极 Prompt
            String templateContent = promptTemplateResource.getContentAsString(StandardCharsets.UTF_8);
            PromptTemplate promptTemplate = PromptTemplate.from(templateContent);

            Map<String, Object> variables = new HashMap<>();
            variables.put("currentDate", LocalDate.now().toString());
            variables.put("context", context);
            variables.put("question", userMessage);
            variables.put("sourceNames", sourceNames(results));
            variables.put("citationIndex", buildCitationIndex(results));

            RagPromptResult result = new RagPromptResult();
            result.setPrompt(promptTemplate.apply(variables).text());
            result.setRetrievedCount(results.size());
            result.setTopScore(results.stream().map(SearchResult::effectiveScore).findFirst().orElse(null));
            result.setRagHit(!results.isEmpty());
            result.setRejected(results.isEmpty());
            result.setMinScoreUsed(minScore);
            result.setRetrievalLatencyMs(System.currentTimeMillis() - startTime);
            result.setSources(toSources(results));
            result.setCitationIndex(buildCitationIndex(results));
            return result;

        } catch (Exception e) {
            log.error("RAG 检索与组装 Prompt 失败", e);
            RagPromptResult result = new RagPromptResult();
            result.setPrompt("你是河南工业大学的校园智能助手。知识库检索暂时不可用，请不要编造答案。"
                    + "请直接回答：抱歉，我的知识库中暂时没有查到相关信息，建议您联系学校相关部门确认。"
                    + "\n\n用户问题：" + userMessage);
            result.setRetrievedCount(0);
            result.setRagHit(false);
            result.setRejected(true);
            result.setMinScoreUsed(minScore);
            result.setRetrievalLatencyMs(System.currentTimeMillis() - startTime);
            return result;
        }
    }

    @Override
    public RagPromptResult buildRagPrompt(List<String> searchQueries, Long categoryId, IntentType intentType) {
        long startTime = System.currentTimeMillis();

        try {
            // 每个子问题独立检索，合并上下文（去重：同一 documentId+chunkIndex 只保留一条）
            Map<String, SearchResult> merged = new LinkedHashMap<>();
            double topScore = 0.0;
            int totalHits = 0;

            for (String query : searchQueries) {
                List<SearchResult> results = hybridSearchService.search(query, categoryId, TOP_K, minScore);
                for (SearchResult r : results) {
                    String key = r.getMetadata().getString("documentId") + "_"
                            + r.getMetadata().getString("chunkIndex");
                    merged.putIfAbsent(key, r);
                    double score = r.effectiveScore();
                    if (score > topScore) topScore = score;
                }
                totalHits += results.size();
            }

            List<SearchResult> allResults = new ArrayList<>(merged.values());
            log.info("【RAG 检索大脑】{} 个子问题，合并后命中 {} 条碎片", searchQueries.size(), allResults.size());

            String context = buildContextWithCitations(allResults);
            if (context.isEmpty()) {
                context = "暂无相关校园文档知识。";
            }

            String templateContent = promptTemplateResource.getContentAsString(StandardCharsets.UTF_8);
            PromptTemplate promptTemplate = PromptTemplate.from(templateContent);

            Map<String, Object> variables = new HashMap<>();
            variables.put("currentDate", LocalDate.now().toString());
            variables.put("context", context);
            variables.put("question", String.join("；", searchQueries));
            variables.put("sourceNames", sourceNames(allResults));
            variables.put("citationIndex", buildCitationIndex(allResults));
            // 办事引导意图时注入引导提示
            if (intentType == IntentType.CAMPUS_GUIDE) {
                variables.put("intentHint", "请注意：用户需要办事流程指引，请按步骤列出操作流程，"
                        + "并告知可能需要联系的具体部门或准备的材料。");
            } else {
                variables.put("intentHint", "");
            }

            RagPromptResult result = new RagPromptResult();
            result.setPrompt(promptTemplate.apply(variables).text());
            result.setRetrievedCount(allResults.size());
            result.setTopScore(topScore > 0 ? topScore : null);
            result.setRagHit(!allResults.isEmpty());
            result.setRejected(allResults.isEmpty());
            result.setMinScoreUsed(minScore);
            result.setRetrievalLatencyMs(System.currentTimeMillis() - startTime);
            result.setSources(toSources(allResults));
            result.setCitationIndex(buildCitationIndex(allResults));
            return result;

        } catch (Exception e) {
            log.error("多子问题 RAG 检索失败", e);
            RagPromptResult result = new RagPromptResult();
            result.setPrompt("你是河南工业大学的校园智能助手。知识库检索暂时不可用，请不要编造答案。"
                    + "请直接回答：抱歉，我的知识库中暂时没有查到相关信息，建议您联系学校相关部门确认。"
                    + "\n\n用户问题：" + String.join("；", searchQueries));
            result.setRetrievedCount(0);
            result.setRagHit(false);
            result.setRejected(true);
            result.setMinScoreUsed(minScore);
            result.setRetrievalLatencyMs(System.currentTimeMillis() - startTime);
            return result;
        }
    }

    private List<RagSource> toSources(List<SearchResult> results) {
        return results.stream()
                .map(r -> {
                    Metadata metadata = r.getMetadata();
                    return new RagSource(
                            parseLong(metadata.getString("documentId")),
                            metadata.getString("fileName"),
                            parseInteger(metadata.getString("chunkIndex")),
                            r.effectiveScore()
                    );
                })
                .toList();
    }

    private String sourceNames(List<SearchResult> results) {
        return toSources(results).stream()
                .map(RagSource::getFileName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining("、"));
    }

    private String buildContextWithCitations(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            Metadata meta = r.getMetadata();
            String fileName = meta.getString("fileName") != null ? meta.getString("fileName") : "未知文档";
            String chunkIdx = meta.getString("chunkIndex") != null ? meta.getString("chunkIndex") : "?";
            sb.append("[来源").append(i + 1).append("：")
              .append(fileName).append("-段落").append(chunkIdx).append("]\n");
            sb.append(r.getText());
            if (i < results.size() - 1) {
                sb.append("\n\n---\n\n");
            }
        }
        return sb.toString();
    }

    private String buildCitationIndex(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            Metadata meta = results.get(i).getMetadata();
            String fileName = meta.getString("fileName") != null ? meta.getString("fileName") : "未知文档";
            String chunkIdx = meta.getString("chunkIndex") != null ? meta.getString("chunkIndex") : "?";
            sb.append("  [").append(i + 1).append("] ")
              .append(fileName).append("（段落").append(chunkIdx).append("）\n");
        }
        return sb.toString();
    }

    @Override
    public String rewriteQuery(String question, String history) {
        if (history == null || history.isBlank()) {
            return question;
        }
        try {
            String templateContent = rewritePromptResource.getContentAsString(StandardCharsets.UTF_8);
            PromptTemplate promptTemplate = PromptTemplate.from(templateContent);

            Map<String, Object> variables = new HashMap<>();
            variables.put("history", history);
            variables.put("question", question);

            String rewritePrompt = promptTemplate.apply(variables).text();
            String rewritten = chatLanguageModel.generate(rewritePrompt).trim();

            if (rewritten.isBlank() || rewritten.length() > question.length() * 3) {
                log.warn("Query Rewrite 结果异常，回退到原始问题: \"{}\"", question);
                return question;
            }

            log.info("Query Rewrite: \"{}\" → \"{}\"", question, rewritten);
            return rewritten;
        } catch (Exception e) {
            log.warn("Query Rewrite 失败，回退到原始问题: \"{}\"", question, e);
            return question;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.valueOf(value);
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.valueOf(value);
    }
}
