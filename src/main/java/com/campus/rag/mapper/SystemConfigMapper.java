package com.campus.rag.mapper;

import com.campus.rag.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SystemConfigMapper {

    List<SystemConfig> selectAll();

    SystemConfig selectByKey(String configKey);

    int insert(SystemConfig config);

    int updateByKey(SystemConfig config);
}
