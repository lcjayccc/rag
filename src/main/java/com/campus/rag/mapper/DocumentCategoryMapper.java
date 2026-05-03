package com.campus.rag.mapper;

import com.campus.rag.entity.DocumentCategory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DocumentCategoryMapper {

    DocumentCategory selectById(Long id);

    DocumentCategory selectByCode(String code);

    List<DocumentCategory> selectAll();

    List<DocumentCategory> selectEnabled();

    int insert(DocumentCategory category);

    int updateById(DocumentCategory category);
}
