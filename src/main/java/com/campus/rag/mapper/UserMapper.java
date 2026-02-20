package com.campus.rag.mapper;

import com.campus.rag.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper {

    User selectById(Long id);

    User selectByUsername(String username);

    int insert(User user);

    int updateById(User user);
}
