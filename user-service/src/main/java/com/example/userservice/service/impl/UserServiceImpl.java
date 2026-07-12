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
        return userMapper.findById(id);
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
    public void changeUserStatus(Long id, Integer status) {
        userMapper.updateStatusById(id, status);
    }
}
