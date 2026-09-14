package com.example.gatewayservice.filter;

import com.example.common.auth.AuthConstants;
import com.example.common.auth.JwtProperties;
import com.example.common.auth.JwtUtil;
import com.example.gatewayservice.config.GatewayAuthProperties;
import com.example.gatewayservice.model.GatewayResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final GatewayAuthProperties gatewayAuthProperties;
    private final ObjectMapper objectMapper;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public JwtAuthGlobalFilter(ReactiveStringRedisTemplate redisTemplate,
                               JwtUtil jwtUtil,
                               JwtProperties jwtProperties,
                               GatewayAuthProperties gatewayAuthProperties,
                               ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
        this.jwtProperties = jwtProperties;
        this.gatewayAuthProperties = gatewayAuthProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!gatewayAuthProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        // 1) 防止客户端伪造 X-Auth-* 头，先清理，再由网关注入可信用户信息
        org.springframework.http.server.reactive.ServerHttpRequest.Builder requestBuilder =
                exchange.getRequest().mutate().headers(headers -> {
            headers.remove(AuthConstants.HEADER_AUTH_USER_ID);
            headers.remove(AuthConstants.HEADER_AUTH_USERNAME);
            headers.remove(AuthConstants.HEADER_AUTH_JTI);
            headers.remove(AuthConstants.HEADER_AUTH_ROLES);
            headers.remove(AuthConstants.HEADER_AUTH_PERMS);
        });

        String path = exchange.getRequest().getURI().getPath();
        if (isWhitelisted(path, gatewayAuthProperties.getWhitelist())) {
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        // 2) 从请求头提取 Bearer Token
        String headerName = jwtProperties.getHeaderName() != null ? jwtProperties.getHeaderName() : AuthConstants.HEADER_AUTHORIZATION;
        String authorization = exchange.getRequest().getHeaders().getFirst(headerName);
        String token = jwtUtil.extractBearerToken(authorization);
        if (token == null) {
            return unauthorized(exchange, "未登录或Token无效");
        }

        // 3) JWT 解析与校验（签名/过期）
        final Claims claims;
        try {
            claims = jwtUtil.parseAndValidate(token);
        } catch (Exception e) {
            return unauthorized(exchange, "未登录或Token无效");
        }

        String userId = claims.getSubject();
        String username = claims.get("username", String.class);
        String jti = claims.getId();
        List<String> roles = toStringList(claims.get(AuthConstants.CLAIM_ROLES));
        List<String> perms = toStringList(claims.get(AuthConstants.CLAIM_PERMS));

        if (userId == null || jti == null) {
            return unauthorized(exchange, "未登录或Token无效");
        }

        // 4) 注销立刻生效：jti 黑名单校验
        String blacklistKey = AuthConstants.blacklistKey(jti);
        Mono<String> blacklistValueMono = redisTemplate.opsForValue().get(blacklistKey);

        // 5) 单点/踢人：userId -> currentJti 必须与当前 token.jti 一致
        String ssoKey = AuthConstants.ssoKey(userId);
        Mono<String> currentJtiMono = redisTemplate.opsForValue().get(ssoKey);

        return blacklistValueMono
                .flatMap(v -> unauthorized(exchange, "未登录或Token无效"))
                .switchIfEmpty(
                        currentJtiMono.flatMap(currentJti -> {
                            if (!jti.equals(currentJti)) {
                                return unauthorized(exchange, "账号已在其他端登录");
                            }

                            // 6) 鉴权通过：向下游注入可信用户信息（下游可直接取 X-Auth-*）
                            requestBuilder.header(AuthConstants.HEADER_AUTH_USER_ID, userId);
                            if (username != null) {
                                requestBuilder.header(AuthConstants.HEADER_AUTH_USERNAME, username);
                            }
                            requestBuilder.header(AuthConstants.HEADER_AUTH_JTI, jti);
                            // RBAC：角色/权限集合转逗号分隔头透传（旧 token 无这两个 claim 则不注入）
                            if (!roles.isEmpty()) {
                                requestBuilder.header(AuthConstants.HEADER_AUTH_ROLES, String.join(",", roles));
                            }
                            if (!perms.isEmpty()) {
                                requestBuilder.header(AuthConstants.HEADER_AUTH_PERMS, String.join(",", perms));
                            }

                            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
                        }).switchIfEmpty(unauthorized(exchange, "未登录或Token无效"))
                );
    }

    @Override
    public int getOrder() {
        return -10;
    }

    private boolean isWhitelisted(String path, List<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        for (String pattern : whitelist) {
            if (antPathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * JWT 中的 roles/perms claim 反序列化为 List&lt;String&gt;；
     * 旧 token 或空集合返回空 List（不注入对应头）。
     */
    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object claimValue) {
        if (claimValue instanceof List) {
            return (List<String>) claimValue;
        }
        return java.util.Collections.emptyList();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().add("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(GatewayResponse.fail(401, message));
        } catch (Exception jsonEx) {
            String fallback = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
            bytes = fallback.getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
