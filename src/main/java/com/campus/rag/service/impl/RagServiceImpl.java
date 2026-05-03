package com.campus.rag.service.impl;

import com.campus.rag.dto.RagPromptResult;
import com.campus.rag.dto.RagSource;
import com.campus.rag.service.RagService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
@Service
public class RagServiceImpl implements RagService {

    private static final int TOP_K = 3;
    private static final double MIN_SCORE = 0.6;

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    // 动态读取外部 .st 模板
    @Value("classpath:prompts/answer-chat-kb.st")
    private Resource promptTemplateResource;

    public RagServiceImpl(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public RagPromptResult buildRagPrompt(String userMessage, Long categoryId) {
        log.info("【RAG 检索大脑】开始为问题寻找答案: \"{}\"", userMessage);
        long startTime = System.currentTimeMillis();

        try {
            // 1. 问题向量化
            Embedding questionEmbedding = embeddingModel.embed(userMessage).content();

            // 2. 内存向量库 Top-K 检索；categoryId 为空时全库检索，不为空时只召回指定分类切片。
            var searchBuilder = EmbeddingSearchRequest.builder()
                    .queryEmbedding(questionEmbedding)
                    .maxResults(TOP_K)
                    .minScore(MIN_SCORE);

            if (categoryId != null) {
                searchBuilder.filter(metadataKey("categoryId").isEqualTo(String.valueOf(categoryId)));
            }

            EmbeddingSearchRequest searchRequest = searchBuilder.build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            log.info("【RAG 检索大脑】命中强相关知识碎片数量: {}", matches.size());

            // 3. 提取碎片文本并拼接
            String context = matches.stream()
                    .map(match -> match.embedded().text())
                    .collect(Collectors.joining("\n\n---\n\n"));

            if (context.isEmpty()) {
                context = "暂无相关校园文档知识。";
            }

            // 4. 组装终极 Prompt
            String templateContent = promptTemplateResource.getContentAsString(StandardCharsets.UTF_8);
            PromptTemplate promptTemplate = PromptTemplate.from(templateContent);

            Map<String, Object> variables = new HashMap<>();
            variables.put("currentDate", LocalDate.now().toString()); // 注入当前时间，解决千问知识截断幻觉
            variables.put("context", context);
            variables.put("question", userMessage);
            variables.put("sourceNames", sourceNames(matches));

            RagPromptResult result = new RagPromptResult();
            result.setPrompt(promptTemplate.apply(variables).text());
            result.setRetrievedCount(matches.size());
            result.setTopScore(matches.stream().map(EmbeddingMatch::score).findFirst().orElse(null));
            result.setRagHit(!matches.isEmpty());
            result.setRejected(matches.isEmpty());
            result.setMinScoreUsed(MIN_SCORE);
            result.setRetrievalLatencyMs(System.currentTimeMillis() - startTime);
            result.setSources(toSources(matches));
            return result;

        } catch (Exception e) {
            log.error("RAG 检索与组装 Prompt 失败", e);
            // 降级策略：如果 RAG 报错，直接返回原问题，保证系统不崩溃，同时保留一次拒答型日志数据。
            RagPromptResult result = new RagPromptResult();
            result.setPrompt("你是河南工业大学的校园智能助手。知识库检索暂时不可用，请不要编造答案。"
                    + "请直接回答：抱歉，我的知识库中暂时没有查到相关信息，建议您联系学校相关部门确认。"
                    + "\n\n用户问题：" + userMessage);
            result.setRetrievedCount(0);
            result.setRagHit(false);
            result.setRejected(true);
            result.setMinScoreUsed(MIN_SCORE);
            result.setRetrievalLatencyMs(System.currentTimeMillis() - startTime);
            return result;
        }
    }

    private List<RagSource> toSources(List<EmbeddingMatch<TextSegment>> matches) {
        return matches.stream()
                .map(match -> {
                    Metadata metadata = match.embedded().metadata();
                    return new RagSource(
                            parseLong(metadata.getString("documentId")),
                            metadata.getString("fileName"),
                            parseInteger(metadata.getString("chunkIndex")),
                            match.score()
                    );
                })
                .toList();
    }

    private String sourceNames(List<EmbeddingMatch<TextSegment>> matches) {
        return toSources(matches).stream()
                .map(RagSource::getFileName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining("、"));
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
