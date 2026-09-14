package com.example.common.role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限点校验注解（RBAC 细粒度：命中任一权限点即放行，语义同 RuoYi 的
 * @PreAuthorize("@ss.hasPermi('system:user:remove')")，只是换成了自有注解）。
 *
 * <p>权限点（perm_code，如 users:list、products:create）维护在数据库
 * sys_permission 表，经 sys_role_permission 绑定给角色、sys_user_role 绑定给用户；
 * 登录时装配进 JWT，网关转 X-Auth-Perms 透传下游，本切面据此判断。</p>
 *
 * <p>给角色增删权限点只需改数据库绑定，下次登录生效，不需要改代码重新发版
 * ——这是 RBAC 相比"角色硬编码"的核心收益。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 允许访问的权限点编码列表，命中其一即放行。
     */
    String[] value();
}
