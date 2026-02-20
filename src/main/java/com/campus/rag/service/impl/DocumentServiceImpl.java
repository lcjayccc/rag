package com.campus.rag.service.impl;

import com.campus.rag.entity.Document;
import com.campus.rag.mapper.DocumentMapper;
import com.campus.rag.service.DocumentService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件解析与切片服务实现
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMapper documentMapper;

    public DocumentServiceImpl(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Override
    public Document upload(Long userId, MultipartFile file) {
        Document doc = new Document();
        doc.setUserId(userId);
        doc.setFileName(file.getOriginalFilename());
        doc.setFilePath(""); // TODO: 实际存储路径
        doc.setFileType(file.getContentType());
        doc.setStatus(0);
        doc.setCreateTime(LocalDateTime.now());
        doc.setUpdateTime(LocalDateTime.now());
        documentMapper.insert(doc);
        return doc;
    }

    @Override
    public List<Document> listByUserId(Long userId) {
        return documentMapper.selectByUserId(userId);
    }

    @Override
    public void deleteById(Long id) {
        documentMapper.deleteById(id);
    }
}
