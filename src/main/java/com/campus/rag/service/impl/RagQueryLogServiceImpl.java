package com.campus.rag.service.impl;

import com.campus.rag.dto.RagPromptResult;
import com.campus.rag.entity.RagQueryLog;
import com.campus.rag.mapper.RagQueryLogMapper;
import com.campus.rag.service.RagQueryLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class RagQueryLogServiceImpl implements RagQueryLogService {

    private final RagQueryLogMapper mapper;

    public RagQueryLogServiceImpl(RagQueryLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(Long userId, String question, RagPromptResult result, long latencyMs) {
        try {
            RagQueryLog logRecord = new RagQueryLog();
            logRecord.setUserId(userId);
            logRecord.setQuestion(question);
            logRecord.setRetrievedCount(result.getRetrievedCount());
            logRecord.setTopScore(result.getTopScore());
            logRecord.setRagHit(result.isRagHit());
            logRecord.setRejected(result.isRejected());
            logRecord.setDocIdsHit(result.sourceDocumentIdsJson());
            logRecord.setMinScoreUsed(result.getMinScoreUsed());
            logRecord.setLatencyMs(toIntLatency(latencyMs));
            logRecord.setCreateTime(LocalDateTime.now());
            mapper.insert(logRecord);
        } catch (Exception e) {
            // 查询日志是观测能力，不能因为日志写入失败影响正常问答。
            log.warn("RAG 查询日志写入失败，已忽略本次日志", e);
        }
    }

    private int toIntLatency(long latencyMs) {
        if (latencyMs > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max((int) latencyMs, 0);
    }
}
