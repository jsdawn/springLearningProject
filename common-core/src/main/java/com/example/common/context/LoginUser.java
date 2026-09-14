package com.example.common.context;

import java.util.Collections;
import java.util.List;

public class LoginUser {

    private final String userId;
    private final String username;
    private final String jti;
    /** 角色编码集合（RBAC：ADMIN/USER/...），来自网关透传的 X-Auth-Roles，可能为空 */
    private final List<String> roles;
    /** 权限点编码集合（RBAC：users:list/...），来自网关透传的 X-Auth-Perms，可能为空 */
    private final List<String> perms;

    public LoginUser(String userId, String username, String jti) {
        this(userId, username, jti, Collections.<String>emptyList(), Collections.<String>emptyList());
    }

    public LoginUser(String userId, String username, String jti,
                     List<String> roles, List<String> perms) {
        this.userId = userId;
        this.username = username;
        this.jti = jti;
        this.roles = roles != null ? roles : Collections.<String>emptyList();
        this.perms = perms != null ? perms : Collections.<String>emptyList();
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

    public List<String> getRoles() {
        return roles;
    }

    public List<String> getPerms() {
        return perms;
    }

    /**
     * 判断当前用户是否拥有指定角色之一。
     */
    public boolean hasAnyRole(String... rolesToCheck) {
        return hasAny(roles, rolesToCheck);
    }

    /**
     * 判断当前用户是否拥有指定权限点之一。
     */
    public boolean hasAnyPermission(String... permsToCheck) {
        return hasAny(perms, permsToCheck);
    }

    private static boolean hasAny(List<String> owned, String[] required) {
        if (owned == null || owned.isEmpty() || required == null || required.length == 0) {
            return false;
        }
        for (String r : required) {
            if (r != null && owned.contains(r)) {
                return true;
            }
        }
        return false;
    }
}
