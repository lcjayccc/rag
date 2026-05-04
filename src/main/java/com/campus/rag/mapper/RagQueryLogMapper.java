package com.campus.rag.mapper;

import com.campus.rag.entity.RagQueryLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RagQueryLogMapper {

    int insert(RagQueryLog log);

    List<RagQueryLog> selectPage(@Param("offset") int offset, @Param("pageSize") int pageSize,
                                  @Param("keyword") String keyword, @Param("ragHit") Boolean ragHit,
                                  @Param("userId") Long userId);

    int countFiltered(@Param("keyword") String keyword, @Param("ragHit") Boolean ragHit,
                      @Param("userId") Long userId);

    int countAll();

    int countToday();

    int countRejected();

    int countHit();

    int avgLatencyMs();
}
