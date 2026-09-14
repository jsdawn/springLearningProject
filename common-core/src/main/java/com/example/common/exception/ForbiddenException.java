package com.example.common.exception;

/**
 * 已登录但角色/权限不足（403 Forbidden）。
 * 由 RoleCheckAspect 在 @RequireRole / @RequirePermission 校验失败时抛出，
 * GlobalExceptionHandler 统一转为 code=403 的 ApiResponse。
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("无权限执行该操作");
    }

    public ForbiddenException(String message) {
        super(message);
    }
}
