package com.campus.rag.service;

import com.campus.rag.entity.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件解析与切片逻辑
 */
public interface DocumentService {

    /**
     * 上传并解析文档，进行切片入库
     *
     * @param userId 用户 ID
     * @param file   上传文件
     * @return 文档实体
     */
    Document upload(Long userId, MultipartFile file);

    /**
     * 查询用户下的文档列表
     */
    List<Document> listByUserId(Long userId);

    /**
     * 根据 ID 删除文档
     */
    void deleteById(Long id);

}
