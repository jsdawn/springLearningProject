package com.example.gatewayservice.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.example.gatewayservice.model.GatewayResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;

/**
 * 网关 Sentinel 限流/熔断统一返回配置。
 *
 * <p>网关为 Reactive 应用，无法使用 Servlet 侧的 BlockExceptionHandler。
 * Sentinel 网关适配器（sentinel-spring-cloud-gateway-adapter）的统一返回通过
 * {@link GatewayCallbackManager#setBlockHandler(BlockRequestHandler)} 静态注册：
 * 触发限流/熔断时，自动装配的 SentinelGatewayBlockExceptionHandler 会调用此处注册的
 * BlockRequestHandler 构造响应体，覆盖默认的 "Blocked by Sentinel" 纯文本。</p>
 *
 * <p>此处输出与 {@link com.example.gatewayservice.handler.GlobalGatewayExceptionHandler}
 * 一致的 {@code GatewayResponse{code, message, data}} JSON 结构（HTTP 200 + 业务 code）。
 * SentinelGatewayFilter / SentinelGatewayBlockExceptionHandler 两个 Bean 由
 * spring-cloud-alibaba-sentinel 自动装配创建，无需手动注册。</p>
 */
@Configuration
public class SentinelGatewayConfig {

    /**
     * 在容器初始化阶段注册自定义网关限流返回处理器，
     * 覆盖 GatewayCallbackManager 默认的 DefaultBlockRequestHandler
     */
    @PostConstruct
    public void init() {
        GatewayCallbackManager.setBlockHandler(new GatewayBlockRequestHandler());
    }

    /**
     * 网关限流/熔断统一响应体构造器
     */
    private static class GatewayBlockRequestHandler implements BlockRequestHandler {

        private static final Logger log = LoggerFactory.getLogger(GatewayBlockRequestHandler.class);

        /** 限流业务码（对齐 HTTP 429 Too Many Requests 语义） */
        private static final int CODE_FLOW_LIMITED = 429;
        /** 熔断降级业务码（对齐 HTTP 503 Service Unavailable 语义） */
        private static final int CODE_DEGRADED = 503;

        @Override
        public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable ex) {
            log.warn("Sentinel 网关规则拦截: uri={}, exception={}",
                    exchange.getRequest().getURI(), ex.getClass().getSimpleName());

            GatewayResponse<Void> body;
            if (ex instanceof DegradeException) {
                body = GatewayResponse.fail(CODE_DEGRADED, "服务熔断降级，请稍后重试");
            } else {
                body = GatewayResponse.fail(CODE_FLOW_LIMITED, "请求太频繁，请稍后再试");
            }

            // 与 GlobalGatewayExceptionHandler 保持一致：HTTP 200，业务状态码放在响应体 code 字段
            return ServerResponse.status(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body);
        }
    }
}
