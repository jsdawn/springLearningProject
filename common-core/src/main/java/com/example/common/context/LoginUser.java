package com.example.common.context;

public class LoginUser {

    private final String userId;
    private final String username;
    private final String jti;

    public LoginUser(String userId, String username, String jti) {
        this.userId = userId;
        this.username = username;
        this.jti = jti;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getJti() {
        return jti;
    }
}

