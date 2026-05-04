package com.campus.rag.service.impl;

import com.campus.rag.entity.SystemConfig;
import com.campus.rag.mapper.SystemConfigMapper;
import com.campus.rag.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigMapper mapper;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(String value, String description) {}

    public SystemConfigServiceImpl(SystemConfigMapper mapper) {
        this.mapper = mapper;
        reload();
    }

    private void reload() {
        List<SystemConfig> all = mapper.selectAll();
        for (SystemConfig c : all) {
            cache.put(c.getConfigKey(), new CacheEntry(c.getConfigValue(), c.getDescription()));
        }
        log.info("Loaded {} system config entries", all.size());
    }

    @Override
    public String get(String key) {
        CacheEntry entry = cache.get(key);
        return entry != null ? entry.value() : null;
    }

    @Override
    public String get(String key, String defaultValue) {
        CacheEntry entry = cache.get(key);
        return entry != null ? entry.value() : defaultValue;
    }

    @Override
    public double getDouble(String key, double defaultValue) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return defaultValue;
        try {
            return Double.parseDouble(entry.value());
        } catch (NumberFormatException e) {
            log.warn("config {}={} 不能解析为 double，使用默认值 {}", key, entry.value(), defaultValue);
            return defaultValue;
        }
    }

    @Override
    public int getInt(String key, int defaultValue) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return defaultValue;
        try {
            return Integer.parseInt(entry.value());
        } catch (NumberFormatException e) {
            log.warn("config {}={} 不能解析为 int，使用默认值 {}", key, entry.value(), defaultValue);
            return defaultValue;
        }
    }

    @Override
    public void put(String key, String value, String description) {
        SystemConfig existing = mapper.selectByKey(key);
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(description);
        if (existing != null) {
            mapper.updateByKey(config);
        } else {
            mapper.insert(config);
        }
        cache.put(key, new CacheEntry(value, description));
    }

    @Override
    public List<ConfigEntry> listAll() {
        return cache.entrySet().stream()
                .map(e -> new ConfigEntry(e.getKey(), e.getValue().value(), e.getValue().description()))
                .toList();
    }
}
