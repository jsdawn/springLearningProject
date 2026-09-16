package com.example.orderservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 订单业务参数（托管在 Nacos 配置中心，支持动态刷新）。
 *
 * <p>配置来源：Nacos dataId = order-service.yaml（本服务 application.yml 的
 * spring.config.import 已引入；本地 yml 不再写这些值，Nacos 未配置时
 * 走这里的代码默认值兜底——配置类即参数契约：字段名、类型、默认值、注释都在代码里）。
 *
 * <p>动态刷新机制（Spring Cloud 2021.x + spring.config.import 模式）：
 * Nacos 控制台发布变更 → 客户端长连接收到通知 → 发布 RefreshEvent →
 * ConfigurationPropertiesRebinder 对所有 @ConfigurationProperties Bean 重新绑定。
 * 因此本类<strong>不需要</strong> @RefreshScope、不需要重启；
 * @Value 注入的字段才需要 @RefreshScope 才能刷新。
 */
@Component
@ConfigurationProperties(prefix = "order.business")
public class OrderBusinessProperties {

    /**
     * 深分页阈值：分页 offset 超过该值时切换为延迟关联 SQL（子查询先定位 id 再回表）。
     * 小偏移两种写法成本相当，直接 LIMIT 更简单；大偏移时延迟关联优势显著。
     * 运营期可按 orders 表体量调整（如数据到十万级可下调到 500）。
     */
    private int deepPageOffsetThreshold = 1000;

    /**
     * 下单幂等令牌有效期（秒）：超时未提交自动作废，客户端需重新领取
     * （防止令牌被长期囤积重放）。
     */
    private long idempotentTokenTtlSeconds = 300;

    public int getDeepPageOffsetThreshold() {
        return deepPageOffsetThreshold;
    }

    public void setDeepPageOffsetThreshold(int deepPageOffsetThreshold) {
        this.deepPageOffsetThreshold = deepPageOffsetThreshold;
    }

    public long getIdempotentTokenTtlSeconds() {
        return idempotentTokenTtlSeconds;
    }

    public void setIdempotentTokenTtlSeconds(long idempotentTokenTtlSeconds) {
        this.idempotentTokenTtlSeconds = idempotentTokenTtlSeconds;
    }
}
