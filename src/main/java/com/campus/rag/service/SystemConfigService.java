package com.campus.rag.service;

import java.util.List;

public interface SystemConfigService {

    String get(String key);

    String get(String key, String defaultValue);

    double getDouble(String key, double defaultValue);

    int getInt(String key, int defaultValue);

    void put(String key, String value, String description);

    List<ConfigEntry> listAll();

    record ConfigEntry(String key, String value, String description) {}
}
