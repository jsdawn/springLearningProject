package com.example.userservice.service.impl;

import com.example.common.auth.AuthConstants;
import com.example.common.auth.JwtProperties;
import com.example.common.auth.JwtUtil;
import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.LoginResponse;
import com.example.userservice.dto.RegisterRequest;
import com.example.userservice.entity.User;
import com.example.userservice.entity.UserCredential;
import com.example.userservice.entity.UserRegister;
import com.example.userservice.mapper.RolePermissionMapper;
import com.example.userservice.mapper.UserMapper;
import com.example.userservice.service.AuthService;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthServiceImpl(UserMapper userMapper,
                           RolePermissionMapper rolePermissionMapper,
                           JwtUtil jwtUtil,
                           JwtProperties jwtProperties,
                           StringRedisTemplate stringRedisTemplate) {
        this.userMapper = userMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.jwtUtil = jwtUtil;
        this.jwtProperties = jwtProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 1) 查询用户凭证信息（包含 password）
        UserCredential credential = userMapper.findCredentialByUsername(request.getUsername());
        if (credential == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (credential.getStatus() == null || credential.getStatus() != 1) {
            throw new IllegalArgumentException("账号已禁用");
        }
        if (credential.getPassword() == null || credential.getPassword().isEmpty()) {
            throw new IllegalArgumentException("账号未设置密码，请先注册");
        }

        // 2) 校验密码（BCrypt）
        boolean match = passwordEncoder.matches(request.getPassword(), credential.getPassword());
        if (!match) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 3) RBAC 装配：查出角色集合与权限点集合，随 token 签发（网关转 X-Auth-* 透传下游）
        //    无绑定的用户 roles/perms 为空集合：仍可登录，但无权访问任何带权限点的接口
        String jti = UUID.randomUUID().toString().replace("-", "");
        String userId = String.valueOf(credential.getId());
        List<String> roles = rolePermissionMapper.findRoleCodesByUsername(request.getUsername());
        List<String> perms = rolePermissionMapper.findPermCodesByUsername(request.getUsername());
        if (roles == null) {
            roles = Collections.emptyList();
        }
        if (perms == null) {
            perms = Collections.emptyList();
        }
        String token = jwtUtil.generateAccessToken(userId, credential.getUsername(), jti, roles, perms);

        // 4) 单点登录：userId -> currentJti（新登录会覆盖旧 jti，从而踢掉旧 token）
        long expireSeconds = jwtProperties.getAccessTokenExpireSeconds() != null
                ? jwtProperties.getAccessTokenExpireSeconds()
                : 3600L;
        stringRedisTemplate.opsForValue().set(AuthConstants.ssoKey(userId), jti, Duration.ofSeconds(expireSeconds));

        return new LoginResponse(token, "Bearer", expireSeconds);
    }

    @Override
    public Long register(RegisterRequest request) {
        // 1) 业务侧做唯一性校验，避免完全依赖数据库唯一索引
        User exist = userMapper.findByUsername(request.getUsername());
        if (exist != null) {
            throw new IllegalArgumentException("用户名已存在（auth.）");
        }

        // 2) 密码强哈希（BCrypt），禁止明文存储
        String encryptedPassword = passwordEncoder.encode(request.getPassword());

        UserRegister userRegister = new UserRegister();
        userRegister.setUsername(request.getUsername());
        userRegister.setPassword(encryptedPassword);
        userRegister.setNickname(request.getNickname());
        userRegister.setPhone(request.getPhone());
        userRegister.setEmail(request.getEmail());
        userRegister.setUserType(2);
        userRegister.setStatus(1);

        int rows = userMapper.insertRegister(userRegister);
        if (rows != 1 || userRegister.getId() == null) {
            throw new IllegalStateException("注册失败");
        }

        return userRegister.getId();
    }

    @Override
    public void logout(String authorizationHeader) {
        // 1) 从 Authorization 提取 token，并解析 jti/exp
        String token = jwtUtil.extractBearerToken(authorizationHeader);
        if (token == null) {
            throw new IllegalArgumentException("未登录或Token无效");
        }

        Claims claims;
        try {
            claims = jwtUtil.parseAndValidate(token);
        } catch (Exception e) {
            throw new IllegalArgumentException("未登录或Token无效");
        }

        String userId = claims.getSubject();
        String jti = claims.getId();
        Date exp = claims.getExpiration();
        if (userId == null || jti == null || exp == null) {
            throw new IllegalArgumentException("未登录或Token无效");
        }

        // 2) 注销立刻生效：jti 写入黑名单，TTL = token 剩余有效期
        long ttlSeconds = Math.max(1, (exp.getTime() - System.currentTimeMillis()) / 1000);
        stringRedisTemplate.opsForValue().set(AuthConstants.blacklistKey(jti), "1", Duration.ofSeconds(ttlSeconds));

        // 3) 如果当前 token 正是单点的 currentJti，则清理登录态
        String ssoKey = AuthConstants.ssoKey(userId);
        String currentJti = stringRedisTemplate.opsForValue().get(ssoKey);
        if (jti.equals(currentJti)) {
            stringRedisTemplate.delete(ssoKey);
        }
    }
}

