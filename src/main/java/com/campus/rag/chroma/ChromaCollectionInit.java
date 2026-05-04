package com.campus.rag.chroma;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动时确保 Chroma collection 存在。
 * 仅在 rag.vector.store=chroma 时激活。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.vector.store", havingValue = "chroma")
public class ChromaCollectionInit {

    private final ChromaClient chromaClient;

    public ChromaCollectionInit(ChromaClient chromaClient) {
        this.chromaClient = chromaClient;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        log.info("[Chroma] 正在初始化 collection: {}", chromaClient.getCollectionName());
        chromaClient.ensureCollection();
    }
}
