package com.campus.rag.mapper;

import com.campus.rag.entity.Document;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 文档 Mapper
 */
@Mapper
public interface DocumentMapper {

    Document selectById(Long id);

    List<Document> selectByUserId(Long userId);

    List<Document> selectAll();

    List<Document> selectCompleted();

    int insert(Document document);

    int updateById(Document document);

    int deleteById(Long id);

    int countAll();

    int countByStatus(int status);

    List<Map<String, Object>> countGroupByCategory();

    List<Document> selectByStatusAndCategory(int status, Long categoryId);
}
