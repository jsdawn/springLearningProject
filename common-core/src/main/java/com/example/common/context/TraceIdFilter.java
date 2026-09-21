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
import java.util.UUID;

/**
 * 链路追踪过滤器：把 traceId / userId 写入 SLF4J MDC，供日志格式 %X{traceId} / %X{userId} 输出。
 *
 * <p>traceId 来源：网关 TraceIdGlobalFilter 生成并通过 X-Trace-Id 头透传；
 * 直连服务端口（本地调试/内部调用）时自动生成。
 * userId 来源：网关鉴权通过后注入的 X-Auth-UserId（白名单请求/内部接口无此头，日志显示 N/A）。
 *
 * <p>Order 必须早于 LoginUserContextFilter（HIGHEST_PRECEDENCE + 10），
 * 保证 MDC 生命周期覆盖整个请求（含更早进入的过滤器抛出的异常日志）。
 *
 * <p>MDC key 与 common-core/src/main/resources/logback/logback-base.xml 的
 * %X{traceId} / %X{userId} 占位符一一对应，改名须两处同步。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(AuthConstants.HEADER_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(MDC_TRACE_ID, traceId);

        String userId = request.getHeader(AuthConstants.HEADER_AUTH_USER_ID);
        if (userId != null && !userId.isEmpty()) {
            MDC.put(MDC_USER_ID, userId);
        }

        // 响应头回传 traceId：前端/调用方报障时可凭它精确定位服务端日志
        response.setHeader(AuthConstants.HEADER_TRACE_ID, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // MDC 基于 ThreadLocal，线程池复用前必须清理，否则串请求污染日志
            MDC.clear();
        }
    }
}
