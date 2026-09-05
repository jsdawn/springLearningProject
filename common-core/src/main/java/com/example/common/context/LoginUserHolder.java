package com.example.common.context;

import com.example.common.exception.UnauthorizedException;

/**
 * 登录用户上下文：基于 ThreadLocal 的请求级作用域。
 *
 * <p>由 LoginUserContextFilter 在请求开始时从网关透传的 Header 写入，
 * 业务层任何位置可通过静态方法读取当前线程对应的登录用户。
 * 请求结束（filter afterCompletion / finally）由 Filter 负责 clear()，
 * 防止线程池复用导致的上下文泄漏。</p>
 */
public final class LoginUserHolder {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private LoginUserHolder() {
    }

    public static void set(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 获取当前登录用户，上下文为空时抛 UnauthorizedException。
     * 适用于「必须登录才能执行业务」的场景（如：下单、创建商品、用户中心）。
     */
    public static LoginUser require() {
        LoginUser loginUser = HOLDER.get();
        if (loginUser == null) {
            throw new UnauthorizedException();
        }
        return loginUser;
    }

    /**
     * 获取当前登录用户的 userId 字符串，未登录抛 UnauthorizedException。
     */
    public static String requireUserId() {
        return require().getUserId();
    }

    /**
     * 获取当前登录用户的 userId（Long 形态），未登录抛 UnauthorizedException。
     * 适用于直接把 userId 写入数据库 Long 主键的场景。
     * 若 JWT subject 无法解析为 Long，会抛 IllegalStateException（业务级异常，由全局处理器转 500）。
     */
    public static Long requireUserIdAsLong() {
        String userId = requireUserId();
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid userId in context, not a number: " + userId, e);
        }
    }
}