package com.example.userservice.service;

import com.example.userservice.entity.User;

import java.util.List;

public interface UserService {

    List<User> listUsers();

    User getUserById(Long id);
}
