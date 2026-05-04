package com.campus.rag.service.impl;

import com.campus.rag.dto.RagPromptResult;
import com.campus.rag.dto.RagSource;
import com.campus.rag.entity.RagQueryLog;
import com.campus.rag.mapper.RagQueryLogMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagQueryLogServiceImplTest {

    @Test
    void recordPersistsRagSearchMetrics() {
        RecordingMapper mapper = new RecordingMapper();
        RagQueryLogServiceImpl service = new RagQueryLogServiceImpl(mapper);
        RagPromptResult result = new RagPromptResult();
        result.setRetrievedCount(2);
        result.setTopScore(0.91);
        result.setRagHit(true);
        result.setRejected(false);
        result.setMinScoreUsed(0.6);
        result.setSources(List.of(
                new RagSource(1L, "奖学金.pdf", 0, 0.91),
                new RagSource(2L, "三好学生.docx", 1, 0.83)
        ));

        service.record(10L, "国家奖学金怎么申请", result, 1234);

        assertEquals(10L, mapper.saved.getUserId());
        assertEquals("国家奖学金怎么申请", mapper.saved.getQuestion());
        assertEquals(2, mapper.saved.getRetrievedCount());
        assertEquals(0.91, mapper.saved.getTopScore());
        assertTrue(mapper.saved.getRagHit());
        assertEquals("[1,2]", mapper.saved.getDocIdsHit());
        assertEquals(1234, mapper.saved.getLatencyMs());
        assertNotNull(mapper.saved.getCreateTime());
    }

    private static class RecordingMapper implements RagQueryLogMapper {
        private RagQueryLog saved;

        @Override
        public int insert(RagQueryLog log) {
            saved = log;
            return 1;
        }

        @Override public List<RagQueryLog> selectPage(int offset, int pageSize, String keyword, Boolean ragHit, Long userId) { return List.of(); }
        @Override public int countFiltered(String keyword, Boolean ragHit, Long userId) { return 0; }
        @Override public int countAll() { return 0; }
        @Override public int countToday() { return 0; }
        @Override public int countRejected() { return 0; }
        @Override public int countHit() { return 0; }
        @Override public int avgLatencyMs() { return 0; }
    }
}
