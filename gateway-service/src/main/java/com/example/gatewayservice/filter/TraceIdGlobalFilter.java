package com.example.gatewayservice.filter;

import com.example.common.auth.AuthConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 链路追踪（网关侧）：生成/透传 X-Trace-Id，下游 Servlet 服务由 common-core 的
 * TraceIdFilter 读入 MDC 输出到日志。
 *
 * <p>为什么不用 MDC：网关是 WebFlux 响应式栈，请求处理跨多个 EventLoop 线程，
 * MDC 基于 ThreadLocal 会串值——网关日志只能显式打印 traceId（见 RequestLogGlobalFilter）。
 *
 * <p>Order 取最小值：保证排在本类之外的鉴权过滤器（order=-10）之前，
 * 连 401 被拦的请求也带 traceId，报障排查不缺入口。
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(AuthConstants.HEADER_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        final String tid = traceId;
        // 请求头透传给下游；响应头回传给调用方（报障时凭响应头即可定位日志）
        exchange.getResponse().getHeaders().set(AuthConstants.HEADER_TRACE_ID, tid);

        return chain.filter(exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> headers.set(AuthConstants.HEADER_TRACE_ID, tid))
                        .build())
                .build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
