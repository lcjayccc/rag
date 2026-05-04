package com.campus.rag.controller;

import com.campus.rag.auth.AuthContext;
import com.campus.rag.common.Result;
import com.campus.rag.dto.QueryLogPageRequest;
import com.campus.rag.entity.RagQueryLog;
import com.campus.rag.mapper.RagQueryLogMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminQueryLogController {

    private final RagQueryLogMapper queryLogMapper;

    public AdminQueryLogController(RagQueryLogMapper queryLogMapper) {
        this.queryLogMapper = queryLogMapper;
    }

    @GetMapping("/query-logs")
    public Result<Map<String, Object>> list(QueryLogPageRequest request) {
        AuthContext.requireAdmin();
        int offset = (request.getPage() - 1) * request.getPageSize();
        List<RagQueryLog> logs = queryLogMapper.selectPage(
                offset, request.getPageSize(), request.getKeyword(), request.getRagHit(), request.getUserId());
        int total = queryLogMapper.countFiltered(request.getKeyword(), request.getRagHit(), request.getUserId());
        return Result.success(Map.of("records", logs, "total", total));
    }
}
