package com.example.userservice.mapper;

import com.example.userservice.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    List<User> findAll();

    User findById(Long id);

    int insert(User user);

    int updateById(User user);

    int updateStatusById(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 分页查询（带可选 keyword 模糊搜索）。
     */
    List<User> findPageByKeyword(@Param("keyword") String keyword,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    /**
     * 统计总数（与分页查询使用同一组过滤条件）。
     */
    long countByKeyword(@Param("keyword") String keyword);
}
