package com.example.common.role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色门槛校验注解（粗粒度：命中任一角色即放行）。
 * 由 RoleCheckAspect 拦截执行，依据网关透传的 X-Auth-Roles 判断。
 *
 * <p>适用场景：整类接口只按"身份"划分（如内部运维接口只允许 ADMIN）。
 * 功能级的精细授权请用 {@link RequirePermission}——权限点绑定在数据库
 * sys_permission 表上，改授权不需要改代码。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * 允许访问的角色编码列表，命中其一即放行。
     */
    String[] value();
}
