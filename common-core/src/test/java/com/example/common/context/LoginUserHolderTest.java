package com.example.common.context;

import com.example.common.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LoginUserHolder 冒烟测试（CI 用）：验证 ThreadLocal 上下文的
 * set/get/clear 生命周期与未登录兜底异常。
 */
class LoginUserHolderTest {

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void setAndGet_roundtrip() {
        LoginUser user = new LoginUser("42", "alice", "jti-1");
        LoginUserHolder.set(user);

        assertThat(LoginUserHolder.get()).isSameAs(user);
        assertThat(LoginUserHolder.requireUserId()).isEqualTo("42");
        assertThat(LoginUserHolder.requireUserIdAsLong()).isEqualTo(42L);
    }

    @Test
    void clear_removesContext() {
        LoginUserHolder.set(new LoginUser("42", "alice", "jti-1"));
        LoginUserHolder.clear();

        assertThat(LoginUserHolder.get()).isNull();
    }

    @Test
    void require_withoutLogin_throwsUnauthorized() {
        assertThatThrownBy(LoginUserHolder::require)
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void requireUserIdAsLong_nonNumeric_throwsIllegalState() {
        LoginUserHolder.set(new LoginUser("not-a-number", "alice", "jti-1"));

        assertThatThrownBy(LoginUserHolder::requireUserIdAsLong)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hasAnyRole_matchesMembership() {
        LoginUser user = new LoginUser("42", "alice", "jti-1",
                Arrays.asList("USER"), Arrays.asList("orders:list"));
        LoginUserHolder.set(user);

        assertThat(user.hasAnyRole("USER", "ADMIN")).isTrue();
        assertThat(user.hasAnyRole("ADMIN")).isFalse();
    }
}
