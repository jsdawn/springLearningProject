package com.example.common.role;

import com.example.common.context.LoginUser;
import com.example.common.context.LoginUserHolder;
import com.example.common.exception.ForbiddenException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * RBAC 校验切面：拦截 {@link RequireRole} / {@link RequirePermission} 注解方法。
 *
 * <p>工作原理与 Spring Security 的 @PreAuthorize 相同——注解 + AOP 自动代理；
 * 区别仅在于角色/权限来源是我们自己的链路
 * （sys_user_role/sys_role_permission → JWT claims → 网关 X-Auth-* → LoginUser），
 * 未引入 Spring Security 全家桶。</p>
 *
 * <p>未登录（LoginUserHolder.require() 抛 UnauthorizedException）返回 401；
 * 已登录但角色/权限不足抛 ForbiddenException 返回 403。</p>
 */
@Aspect
@Component
public class RoleCheckAspect {

    private static final Logger log = LoggerFactory.getLogger(RoleCheckAspect.class);

    @Before("@annotation(requireRole)")
    public void checkRole(RequireRole requireRole) {
        LoginUser loginUser = LoginUserHolder.require();

        if (!loginUser.hasAnyRole(requireRole.value())) {
            log.warn("Role check denied: userId={}, roles={}, required={}",
                    loginUser.getUserId(), loginUser.getRoles(), requireRole.value());
            throw new ForbiddenException("无权限执行该操作，需要角色：" + String.join("/", requireRole.value()));
        }
    }

    @Before("@annotation(requirePermission)")
    public void checkPermission(RequirePermission requirePermission) {
        LoginUser loginUser = LoginUserHolder.require();

        if (!loginUser.hasAnyPermission(requirePermission.value())) {
            log.warn("Permission check denied: userId={}, perms={}, required={}",
                    loginUser.getUserId(), loginUser.getPerms(), requirePermission.value());
            throw new ForbiddenException("无权限执行该操作，需要权限点：" + String.join("/", requirePermission.value()));
        }
    }
}
