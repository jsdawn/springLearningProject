package com.example.common.exception;

/**
 * 重复提交异常（幂等 token 已被消费或已失效）。
 *
 * <p>由 IdempotentTokenService.consumeToken() 在 Lua 脚本原子消费失败时抛出，
 * GlobalExceptionHandler 会捕获并返回 HTTP 409 Conflict。</p>
 *
 * <p>典型场景：前端重复点击提交按钮、网络超时后的自动重试、令牌超过有效期（5 分钟）后重放。</p>
 */
public class DuplicateSubmitException extends RuntimeException {

    public DuplicateSubmitException() {
        super("请勿重复提交");
    }

    public DuplicateSubmitException(String message) {
        super(message);
    }
}
