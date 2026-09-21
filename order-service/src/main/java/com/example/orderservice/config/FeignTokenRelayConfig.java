package com.example.orderservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Configuration
public class FeignTokenRelayConfig {

    @Bean
    public RequestInterceptor tokenRelayRequestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // 服务 A -> Feign -> 服务 B 场景下透传 Authorization，保证下游仍能识别用户身份
                RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
                if (attributes instanceof ServletRequestAttributes) {
                    HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
                    String authorization = request.getHeader("Authorization");
                    if (authorization != null && !authorization.isEmpty()) {
                        template.header("Authorization", authorization);
                    }
                }

                // 链路追踪：Feign 同步调用跑在请求线程上，MDC 可直接取；
                // 下游服务的 TraceIdFilter 读该头写进自己的 MDC，跨服务日志靠它串起来
                String traceId = MDC.get("traceId");
                if (traceId != null && !traceId.isEmpty()) {
                    template.header("X-Trace-Id", traceId);
                }
            }
        };
    }
}
