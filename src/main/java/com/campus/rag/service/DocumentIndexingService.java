package com.campus.rag.service;

import com.campus.rag.entity.Document;

import java.nio.file.Path;

public interface DocumentIndexingService {

    void index(Document document, Path filePath);
}
