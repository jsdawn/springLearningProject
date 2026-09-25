package com.example.gatewayservice.filter;

import brave.Tracer;
import com.example.common.auth.AuthConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 链路追踪（网关侧，Sleuth 接管后的瘦身版）：
 * traceId 的生成与向下游传播（b3 头）已由 Sleuth 自动完成，本类只保留一件事——
 * 把 Sleuth 当前 traceId 回填到响应头 X-Trace-Id，调用方报障时凭响应头即可定位日志与 Zipkin 链路。
 * 从 Tracer 取值保证响应头、网关日志 MDC、Zipkin 上的 traceId 三者同源。
 *
 * <p>Order 取 HIGHEST_PRECEDENCE + 10：必须排在 Sleuth 的 WebFlux 埋点过滤器
 * （HIGHEST_PRECEDENCE + 5）之内才能从 Tracer 拿到当前 span 上下文；
 * 同时仍早于鉴权过滤器（-10），401 被拦的响应也带回 traceId。
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdGlobalFilter.class);

    private final Tracer tracer;

    public TraceIdGlobalFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        brave.Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            exchange.getResponse().getHeaders().set(
                    AuthConstants.HEADER_TRACE_ID, currentSpan.context().traceIdString());
        } else {
            log.warn("no active span, skip X-Trace-Id response header");
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
