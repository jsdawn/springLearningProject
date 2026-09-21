package com.example.common.auth;

public final class AuthConstants {

    private AuthConstants() {
    }

    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    public static final String HEADER_AUTH_USER_ID = "X-Auth-UserId";
    public static final String HEADER_AUTH_USERNAME = "X-Auth-Username";
    public static final String HEADER_AUTH_JTI = "X-Auth-Jti";
    /** 网关注入的角色集合头（逗号分隔，如 X-Auth-Roles: ADMIN,OPERATOR） */
    public static final String HEADER_AUTH_ROLES = "X-Auth-Roles";
    /** 网关注入的权限点集合头（逗号分隔，如 X-Auth-Perms: users:list,users:page） */
    public static final String HEADER_AUTH_PERMS = "X-Auth-Perms";

    /** JWT 中携带角色集合 / 权限点集合的 claim 名（RBAC） */
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMS = "perms";

    /** 链路追踪头：网关生成/透传，下游服务读取后写入 MDC（日志格式含 traceId） */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    public static final String REDIS_KEY_PREFIX_BLACKLIST = "auth:blacklist:";
    public static final String REDIS_KEY_PREFIX_SSO = "auth:sso:";

    public static String blacklistKey(String jti) {
        return REDIS_KEY_PREFIX_BLACKLIST + jti;
    }

    public static String ssoKey(String userId) {
        return REDIS_KEY_PREFIX_SSO + userId;
    }
}

