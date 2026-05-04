package com.campus.rag.controller;

import com.campus.rag.auth.AuthContext;
import com.campus.rag.common.Result;
import com.campus.rag.service.SystemConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminConfigController {

    private final SystemConfigService configService;

    public AdminConfigController(SystemConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/configs")
    public Result<List<SystemConfigService.ConfigEntry>> list() {
        AuthContext.requireAdmin();
        return Result.success(configService.listAll());
    }

    @PutMapping("/configs")
    public Result<?> update(@RequestBody Map<String, String> body) {
        AuthContext.requireAdmin();
        String key = body.get("key");
        String value = body.get("value");
        if (key == null || key.isBlank()) {
            return Result.error("config key 不能为空");
        }
        configService.put(key, value, body.getOrDefault("description", ""));
        return Result.success();
    }
}
