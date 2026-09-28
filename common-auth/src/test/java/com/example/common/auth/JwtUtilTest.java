package com.example.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtUtil 冒烟测试（CI 用）：不依赖 Spring 上下文与外部服务，
 * 覆盖签发/解析往返、claims 装配、过期拒绝、Bearer 前缀提取、非法密钥拒绝。
 */
class JwtUtilTest {

    private JwtProperties props;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        // HS256 要求密钥 >= 256bit（32 字节），用 48 字节明文的 Base64 满足
        props.setSecretBase64(Base64.getEncoder()
                .encodeToString("ci-smoke-test-secret-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        props.setIssuer("mall-test");
        props.setAccessTokenExpireSeconds(3600L);
    }

    @Test
    void generateAndParseRoundtrip_keepsClaims() {
        JwtUtil util = new JwtUtil(props);
        List<String> roles = Arrays.asList("ADMIN", "USER");
        List<String> perms = Collections.singletonList("users:list");

        String token = util.generateAccessToken("1", "admin", "jti-001", roles, perms);
        Claims claims = util.parseAndValidate(token);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.getId()).isEqualTo("jti-001");
        assertThat(claims.getIssuer()).isEqualTo("mall-test");
        assertThat(claims.get("username", String.class)).isEqualTo("admin");
        assertThat((List<?>) claims.get(AuthConstants.CLAIM_ROLES)).containsExactly("ADMIN", "USER");
        assertThat((List<?>) claims.get(AuthConstants.CLAIM_PERMS)).containsExactly("users:list");
    }

    @Test
    void expiredToken_isRejected() {
        props.setAccessTokenExpireSeconds(-10L); // 已过期
        JwtUtil util = new JwtUtil(props);
        String token = util.generateAccessToken("1", "admin", "jti-002", null, null);

        assertThatThrownBy(() -> util.parseAndValidate(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void extractBearerToken_handlesEdgeCases() {
        JwtUtil util = new JwtUtil(props);

        assertThat(util.extractBearerToken(null)).isNull();
        assertThat(util.extractBearerToken("")).isNull();
        assertThat(util.extractBearerToken("Basic xxxx")).isNull();
        assertThat(util.extractBearerToken("Bearer abc.def.ghi")).isEqualTo("abc.def.ghi");
        assertThat(util.extractBearerToken("Bearer   abc.def.ghi  ")).isEqualTo("abc.def.ghi");
    }

    @Test
    void blankSecret_rejectedAtConstruction() {
        props.setSecretBase64("  ");
        assertThatThrownBy(() -> new JwtUtil(props))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
