package com.campus.rag.controller;

import com.campus.rag.auth.AuthContext;
import com.campus.rag.common.Result;
import com.campus.rag.dto.StatOverviewResponse;
import com.campus.rag.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminStatsController {

    private final StatsService statsService;

    public AdminStatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    public Result<StatOverviewResponse> stats() {
        AuthContext.requireAdmin();
        return Result.success(statsService.overview());
    }
}
