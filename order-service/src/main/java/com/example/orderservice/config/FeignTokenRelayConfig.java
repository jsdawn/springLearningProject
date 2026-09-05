package com.example.orderservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
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
                if (!(attributes instanceof ServletRequestAttributes)) {
                    return;
                }
                HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
                String authorization = request.getHeader("Authorization");
                if (authorization == null || authorization.isEmpty()) {
                    return;
                }
                template.header("Authorization", authorization);
            }
        };
    }
}
