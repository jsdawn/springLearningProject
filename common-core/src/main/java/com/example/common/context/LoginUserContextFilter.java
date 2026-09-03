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

        if (userId != null && !userId.isEmpty()) {
            LoginUserHolder.set(new LoginUser(userId, username, jti));
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            LoginUserHolder.clear();
        }
    }
}

