package com.example.common.context;

import com.example.common.auth.AuthConstants;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 用户标识日志过滤器：把 userId 写入 SLF4J MDC，供日志格式 %X{userId} 输出。
 *
 * <p>userId 来源：网关鉴权通过后注入的 X-Auth-UserId（白名单请求/内部接口无此头，日志显示 N/A）。
 *
 * <p>traceId 不再由本类管理：阶段13 接入 Sleuth 后，traceId 的生成、跨服务传播（b3 头）
 * 与 MDC 写入（键名同为 traceId）全部由 Sleuth 自动完成，本类只负责它覆盖不了的 userId。
 *
 * <p>Order 必须早于 LoginUserContextFilter（HIGHEST_PRECEDENCE + 10），
 * 保证 MDC 生命周期覆盖整个请求（含更早进入的过滤器抛出的异常日志）。
 *
 * <p>注意 finally 只 remove 自己的 key，不能 MDC.clear()——
 * 那会把 Sleuth 写入的 traceId/spanId 一并清掉。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserIdMdcFilter extends OncePerRequestFilter {

    public static final String MDC_USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(AuthConstants.HEADER_AUTH_USER_ID);
        if (userId != null && !userId.isEmpty()) {
            MDC.put(MDC_USER_ID, userId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_USER_ID);
        }
    }
}
