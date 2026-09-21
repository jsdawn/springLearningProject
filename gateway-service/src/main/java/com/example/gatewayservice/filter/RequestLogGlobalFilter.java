package com.example.gatewayservice.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestLogGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLogGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String method = exchange.getRequest().getMethodValue();
        String path = exchange.getRequest().getURI().getRawPath();
        // TraceIdGlobalFilter（order 最小）已把 traceId 写进请求头，此处直接读；
        // WebFlux 下 MDC 跨 EventLoop 线程不可靠，网关日志须显式携带
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        log.info("Gateway Request -> {} {} [traceId={}]", method, path, traceId);
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}

