package com.example.common.sentinel;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.example.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * Sentinel 限流/熔断降级统一返回处理器（Servlet 微服务通用）。
 *
 * <p>Sentinel 触发流控、熔断降级、热点参数限流、黑白名单、系统保护规则时，
 * 会抛出对应的 {@link BlockException}，默认返回纯文本 "Blocked by Sentinel"，
 * 这里统一转换为与 {@link com.example.common.exception.GlobalExceptionHandler}
 * 一致的 {@code ApiResponse{code, message, data}} JSON 结构（HTTP 200 + 业务 code）。</p>
 *
 * <p>注意：该处理器仅注册在 Servlet 类型的微服务（user/product/order），
 * 网关 gateway-service 为 Reactive 应用，使用独立的
 * {@code SentinelGatewayBlockExceptionHandler}，不受本类影响。</p>
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SentinelBlockExceptionHandler implements BlockExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SentinelBlockExceptionHandler.class);

    /** 限流业务码（对齐 HTTP 429 Too Many Requests 语义） */
    private static final int CODE_FLOW_LIMITED = 429;
    /** 熔断降级业务码（对齐 HTTP 503 Service Unavailable 语义） */
    private static final int CODE_DEGRADED = 503;
    /** 黑白名单拦截业务码（对齐 HTTP 403 Forbidden 语义） */
    private static final int CODE_FORBIDDEN = 403;

    private final ObjectMapper objectMapper;

    public SentinelBlockExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, BlockException e) throws Exception {
        log.warn("Sentinel 规则拦截: uri={}, resource={}, rule={}, exception={}",
                request.getRequestURI(), e.getRuleLimitApp(), e.getRule(), e.getClass().getSimpleName());

        ApiResponse<Void> body = resolveBody(e);

        // 与 GlobalExceptionHandler 保持一致：HTTP 200，业务状态码放在响应体 code 字段
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /**
     * 按 Sentinel 异常类型映射业务码与提示语。
     * 注意：ParamFlowException 继承自 FlowException，必须先判断热点参数限流。
     */
    private ApiResponse<Void> resolveBody(BlockException e) {
        if (e instanceof DegradeException) {
            return ApiResponse.fail(CODE_DEGRADED, "服务熔断降级，请稍后重试");
        }
        if (e instanceof ParamFlowException) {
            return ApiResponse.fail(CODE_FLOW_LIMITED, "热点参数限流，请稍后重试");
        }
        if (e instanceof AuthorityException) {
            return ApiResponse.fail(CODE_FORBIDDEN, "请求被黑白名单规则拦截");
        }
        if (e instanceof SystemBlockException) {
            return ApiResponse.fail(CODE_DEGRADED, "系统负载过高，已触发保护规则，请稍后重试");
        }
        if (e instanceof FlowException) {
            return ApiResponse.fail(CODE_FLOW_LIMITED, "请求太频繁，请稍后再试");
        }
        return ApiResponse.fail(CODE_FLOW_LIMITED, "请求被 Sentinel 限流");
    }
}
