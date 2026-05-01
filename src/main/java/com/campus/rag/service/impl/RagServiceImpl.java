package com.campus.rag.service.impl;

import com.campus.rag.service.RagService;
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

@Slf4j
@Service
public class RagServiceImpl implements RagService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    // 动态读取外部 .st 模板
    @Value("classpath:prompt/answer-chat-kb.st")
    private Resource promptTemplateResource;

    public RagServiceImpl(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public String buildRagPrompt(String userMessage) {
        log.info("【RAG 检索大脑】开始为问题寻找答案: \"{}\"", userMessage);

        try {
            // 1. 问题向量化
            Embedding questionEmbedding = embeddingModel.embed(userMessage).content();

            // 2. 内存向量库 Top-3 检索，minScore 过滤低相关度垃圾信息
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(questionEmbedding)
                    .maxResults(3)
                    .minScore(0.6)
                    .build();

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

            return promptTemplate.apply(variables).text();

        } catch (Exception e) {
            log.error("RAG 检索与组装 Prompt 失败", e);
            // 降级策略：如果 RAG 报错，直接返回原问题，保证系统不崩溃
            return userMessage;
        }
    }
}
