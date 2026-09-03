package com.example.userservice.service;

import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.LoginResponse;
import com.example.userservice.dto.RegisterRequest;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    Long register(RegisterRequest request);

    void logout(String authorizationHeader);
}

