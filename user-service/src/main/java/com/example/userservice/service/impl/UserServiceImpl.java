package com.example.userservice.service.impl;

import com.example.userservice.entity.User;
import com.example.userservice.mapper.UserMapper;
import com.example.userservice.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<User> listUsers() {
        return userMapper.findAll();
    }

    @Override
    public User getUserById(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new IllegalArgumentException("User not found, id=" + id);
        }
        return user;
    }

    @Override
    public User createUser(User user) {
        int rows = userMapper.insert(user);
        if (rows <= 0 || user.getId() == null) {
            throw new IllegalStateException("Create user failed");
        }
        return userMapper.findById(user.getId());
    }

    @Override
    public User updateUser(User user) {
        int rows = userMapper.updateById(user);
        if (rows <= 0) {
            throw new IllegalStateException("Update user failed, id=" + user.getId());
        }
        return userMapper.findById(user.getId());
    }

    @Override
    public User changeUserStatus(Long id, Integer status) {
        int rows = userMapper.updateStatusById(id, status);
        if (rows <= 0) {
            throw new IllegalStateException("Change user status failed, id=" + id);
        }
        return getUserById(id);
    }
}
