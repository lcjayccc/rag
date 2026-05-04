package com.campus.rag.chroma;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rag.chroma")
public class ChromaProperties {

    private String baseUrl = "http://localhost:8000";
    private String tenant = "default_tenant";
    private String database = "default_database";
    private String collectionName = "campus_rag";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }
}
