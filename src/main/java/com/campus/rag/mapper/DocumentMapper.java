package com.campus.rag.mapper;

import com.campus.rag.entity.Document;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 文档 Mapper
 */
@Mapper
public interface DocumentMapper {

    Document selectById(Long id);

    List<Document> selectByUserId(Long userId);

    int insert(Document document);

    int updateById(Document document);

    int deleteById(Long id);
}
