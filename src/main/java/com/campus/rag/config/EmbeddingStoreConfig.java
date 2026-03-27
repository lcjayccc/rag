package com.campus.rag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储配置
 *
 * <p>注册 InMemoryEmbeddingStore 为 Spring 单例 Bean，生命周期与应用一致。
 * 服务重启后由 KnowledgeWarmupService（Step 9）自动重新填充。
 */
@Configuration
public class EmbeddingStoreConfig {

    @Bean
    public InMemoryEmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
}
