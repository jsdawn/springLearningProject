package com.example.common.auth;

public final class AuthConstants {

    private AuthConstants() {
    }

    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    public static final String HEADER_AUTH_USER_ID = "X-Auth-UserId";
    public static final String HEADER_AUTH_USERNAME = "X-Auth-Username";
    public static final String HEADER_AUTH_JTI = "X-Auth-Jti";

    public static final String REDIS_KEY_PREFIX_BLACKLIST = "auth:blacklist:";
    public static final String REDIS_KEY_PREFIX_SSO = "auth:sso:";

    public static String blacklistKey(String jti) {
        return REDIS_KEY_PREFIX_BLACKLIST + jti;
    }

    public static String ssoKey(String userId) {
        return REDIS_KEY_PREFIX_SSO + userId;
    }
}

