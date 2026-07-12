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
}
