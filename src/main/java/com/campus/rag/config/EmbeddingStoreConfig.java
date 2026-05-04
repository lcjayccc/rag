package com.campus.rag.config;

import com.campus.rag.chroma.ChromaClient;
import com.campus.rag.chroma.ChromaEmbeddingStoreAdapter;
import com.campus.rag.chroma.ChromaProperties;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ChromaProperties.class)
public class EmbeddingStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "rag.vector.store", havingValue = "inmemory", matchIfMissing = true)
    public EmbeddingStore<TextSegment> inMemoryEmbeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    @ConditionalOnProperty(name = "rag.vector.store", havingValue = "chroma")
    public EmbeddingStore<TextSegment> chromaEmbeddingStore(ChromaClient chromaClient) {
        return new ChromaEmbeddingStoreAdapter(chromaClient);
    }
}
