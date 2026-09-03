package com.example.userservice.dto;

public class LoginResponse {

    private String token;
    private String tokenType;
    private Long expireSeconds;

    public LoginResponse() {
    }

    public LoginResponse(String token, String tokenType, Long expireSeconds) {
        this.token = token;
        this.tokenType = tokenType;
        this.expireSeconds = expireSeconds;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(Long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }
}

