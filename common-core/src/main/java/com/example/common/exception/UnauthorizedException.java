package com.example.common.exception;

/**
 * 未登录 / 登录已失效异常。
 *
 * <p>由 LoginUserHolder.required() 在 ThreadLocal 上下文为空时抛出，
 * GlobalExceptionHandler 会捕获并返回 HTTP 401。</p>
 *
 * <p>典型场景：网关透传的 X-Auth-* Header 缺失（如绕过网关直接调用、Token 黑名单命中后仍访问、
 * 下游服务被单独访问而非经网关），业务层读取当前登录用户时会进入此分支。</p>
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("未登录或登录已失效");
    }

    public UnauthorizedException(String message) {
        super(message);
    }
}