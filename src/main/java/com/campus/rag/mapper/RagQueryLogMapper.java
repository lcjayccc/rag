package com.campus.rag.mapper;

import com.campus.rag.entity.RagQueryLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RagQueryLogMapper {

    int insert(RagQueryLog log);
}
