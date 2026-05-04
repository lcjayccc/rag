package com.campus.rag.service.impl;

import com.campus.rag.dto.StatOverviewResponse;
import com.campus.rag.mapper.DocumentCategoryMapper;
import com.campus.rag.mapper.DocumentMapper;
import com.campus.rag.mapper.RagQueryLogMapper;
import com.campus.rag.service.StatsService;
import com.campus.rag.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsServiceImpl implements StatsService {

    private final DocumentMapper documentMapper;
    private final RagQueryLogMapper queryLogMapper;
    private final DocumentCategoryMapper categoryMapper;
    private final SystemConfigService configService;

    @Value("${rag.vector.store:chroma}")
    private String vectorStore;

    @Value("${rag.hybrid.enabled:true}")
    private boolean hybridEnabled;

    public StatsServiceImpl(DocumentMapper documentMapper, RagQueryLogMapper queryLogMapper,
                           DocumentCategoryMapper categoryMapper, SystemConfigService configService) {
        this.documentMapper = documentMapper;
        this.queryLogMapper = queryLogMapper;
        this.categoryMapper = categoryMapper;
        this.configService = configService;
    }

    @Override
    public StatOverviewResponse overview() {
        return StatOverviewResponse.builder()
                .documents(buildDocumentStats())
                .queries(buildQueryStats())
                .system(buildSystemStatus())
                .build();
    }

    private StatOverviewResponse.DocumentStats buildDocumentStats() {
        int total = documentMapper.countAll();
        int completed = documentMapper.countByStatus(2);
        int processing = documentMapper.countByStatus(0) + documentMapper.countByStatus(1);
        int failed = documentMapper.countByStatus(3);

        var categoriesById = categoryMapper.selectAll().stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));

        Map<String, Integer> byCategory = new LinkedHashMap<>();
        List<Map<String, Object>> rows = documentMapper.countGroupByCategory();
        for (Map<String, Object> row : rows) {
            Object categoryId = row.get("category_id");
            Object cnt = row.get("cnt");
            if (categoryId != null && cnt != null) {
                String name = "未分类";
                if (categoryId instanceof Long) {
                    name = categoriesById.getOrDefault(categoryId, "未分类");
                }
                byCategory.put(name, ((Number) cnt).intValue());
            }
        }

        return StatOverviewResponse.DocumentStats.builder()
                .total(total).completed(completed).processing(processing).failed(failed)
                .byCategory(byCategory)
                .build();
    }

    private StatOverviewResponse.QueryStats buildQueryStats() {
        int total = queryLogMapper.countAll();
        int today = queryLogMapper.countToday();
        int rejected = queryLogMapper.countRejected();
        int hitCount = queryLogMapper.countHit();
        double hitRate = total > 0 ? (double) hitCount / total : 0.0;
        int avgLatency = queryLogMapper.avgLatencyMs();

        return StatOverviewResponse.QueryStats.builder()
                .total(total).today(today).hitRate(hitRate).avgLatencyMs(avgLatency).rejectedCount(rejected)
                .build();
    }

    private StatOverviewResponse.SystemStatus buildSystemStatus() {
        return StatOverviewResponse.SystemStatus.builder()
                .vectorStore(vectorStore)
                .hybridEnabled(hybridEnabled)
                .minScore(configService.getDouble("rag.retrieval.min-score", 0.65))
                .topK(configService.getInt("rag.retrieval.top-k", 3))
                .build();
    }
}
