package com.example.gatewayservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GatewayAuthProperties 绑定冒烟测试（CI 用）：不启动 WebFlux 上下文，
 * 用 Binder 直接验证 gateway.auth.* 配置项（含 Nacos 下发的 kebab-case 键）
 * 能正确绑定到 @ConfigurationProperties 类——这是白名单动态刷新的契约基础。
 */
class GatewayAuthPropertiesTest {

    private GatewayAuthProperties bind(Map<String, Object> source) {
        ConfigurationPropertySource cps = new MapConfigurationPropertySource(source);
        BindResult<GatewayAuthProperties> result =
                new Binder(cps).bind("gateway.auth", GatewayAuthProperties.class);
        return result.get();
    }

    @Test
    void whitelistList_bindsFromIndexedKeys() {
        Map<String, Object> source = new HashMap<>();
        source.put("gateway.auth.whitelist[0]", "/api/user/auth/login");
        source.put("gateway.auth.whitelist[1]", "/api/user/auth/register");

        GatewayAuthProperties props = bind(source);

        assertThat(props.getWhitelist())
                .containsExactly("/api/user/auth/login", "/api/user/auth/register");
    }

    @Test
    void enabled_bindsFromBoolean() {
        Map<String, Object> source = new HashMap<>();
        source.put("gateway.auth.enabled", false);

        assertThat(bind(source).isEnabled()).isFalse();
    }

    @Test
    void emptySource_keepsDefaults() {
        Map<String, Object> source = new HashMap<>();

        // 无 gateway.auth.* 配置键时 Binder 不产生绑定（unbound），
        // Spring 实际行为是直接实例化 bean 沿用字段默认值
        ConfigurationPropertySource cps = new MapConfigurationPropertySource(source);
        BindResult<GatewayAuthProperties> result =
                new Binder(cps).bind("gateway.auth", GatewayAuthProperties.class);
        assertThat(result.isBound()).isFalse();

        GatewayAuthProperties props = new GatewayAuthProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getWhitelist()).isEmpty();
    }
}
