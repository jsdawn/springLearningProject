package com.example.userservice.service;

import com.example.common.response.PageResult;
import com.example.userservice.dto.UserPageQuery;
import com.example.userservice.entity.User;

import java.util.List;

public interface UserService {

    List<User> listUsers();

    User getUserById(Long id);

    User createUser(User user);

    User updateUser(User user);

    User changeUserStatus(Long id, Integer status);

    /**
     * 用户分页查询（手写 LIMIT 分页，便于理解 MyBatis 分页实现）。
     */
    PageResult<User> pageUsers(UserPageQuery query);
}
