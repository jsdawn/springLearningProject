package com.example.common.context;

import com.example.common.auth.AuthConstants;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LoginUserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 网关鉴权通过后会注入 X-Auth-*，下游服务可在任何层通过 LoginUserHolder.get() 获取当前登录用户
        String userId = request.getHeader(AuthConstants.HEADER_AUTH_USER_ID);
        String username = request.getHeader(AuthConstants.HEADER_AUTH_USERNAME);
        String jti = request.getHeader(AuthConstants.HEADER_AUTH_JTI);
        // RBAC：角色/权限点集合，网关以逗号分隔头透传（如 X-Auth-Perms: users:list,users:page）
        List<String> roles = splitCsv(request.getHeader(AuthConstants.HEADER_AUTH_ROLES));
        List<String> perms = splitCsv(request.getHeader(AuthConstants.HEADER_AUTH_PERMS));

        if (userId != null && !userId.isEmpty()) {
            LoginUserHolder.set(new LoginUser(userId, username, jti, roles, perms));
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            LoginUserHolder.clear();
        }
    }

    /**
     * 逗号分隔的头值解析为列表；空值/空白返回空列表。
     */
    private List<String> splitCsv(String headerValue) {
        List<String> result = new ArrayList<>();
        if (headerValue == null || headerValue.isEmpty()) {
            return result;
        }
        for (String part : headerValue.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
