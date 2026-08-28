package com.example.orderservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑与可靠投递配置：订单超时关闭（死信延时队列方案）。
 *
 * <p>整体消息流转（核心原理：消息在"延时队列"里待满 TTL 后过期，被 RabbitMQ 自动转发到"死信队列"，
 * 由死信消费者处理，从而实现"延时触发"）：
 *
 * <pre>
 * 生产者 ──order.delay.key──▶ order.delay.exchange (direct)
 *        ──▶ order.delay.queue（延时队列：无消费者，x-message-ttl=30分钟）
 *              │  消息到期过期
 *              ▼  通过 x-dead-letter-exchange / x-dead-letter-routing-key 转发
 *        order.dlx.exchange (direct) ──order.dlx.key──▶ order.dlx.queue（死信队列）
 *              ──▶ OrderDeadLetterConsumer 消费：判断订单是否仍待支付，是则关单+回补库存
 * </pre>
 *
 * <p>防消息丢失三道防线（与 application.yml 中 spring.rabbitmq 配置对应）：
 * <ol>
 *   <li>publisher-confirm-type: correlated —— 消息是否到达 Broker，由 ConfirmCallback 告知；</li>
 *   <li>publisher-returns + mandatory —— 消息到达 Broker 但路由不到队列时，由 ReturnsCallback 退回；</li>
 *   <li>acknowledge-mode: manual —— 消费者处理完业务后手动 basicAck，失败则 basicNack 重投。</li>
 * </ol>
 *
 * <p>注意：队列/交换机声明是幂等的，但"同名队列参数不同"会启动报错（PRECONDITION_FAILED）。
 * 若曾在管理台手动建过同名队列且参数不一致，请先在管理台删除旧队列再启动。
 */
@Configuration
public class RabbitMqConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqConfig.class);

    // ==================== 拓扑名称常量（生产者/消费者/测试接口统一引用，避免魔法字符串） ====================

    /** 业务延时交换机：下单成功后消息先进这里 */
    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    /** 业务延时队列：消息在此"睡眠"到 TTL 过期，本身没有消费者 */
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    /** 延时交换机 → 延时队列 的路由键 */
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay.key";

    /** 死信交换机：延时队列中的过期消息被转发到这里 */
    public static final String ORDER_DLX_EXCHANGE = "order.dlx.exchange";
    /** 死信队列：真正被消费的队列（订单超时关闭逻辑入口） */
    public static final String ORDER_DLX_QUEUE = "order.dlx.queue";
    /** 死信交换机 → 死信队列 的路由键 */
    public static final String ORDER_DLX_ROUTING_KEY = "order.dlx.key";

    /**
     * 延时时长（毫秒），默认 30 分钟 = 1800000。
     * 注意：该值同时被 RabbitMqConfig（队列 TTL）与 OrderDelayMessageProducer（默认消息 TTL）引用，
     * 修改 application.yml 的 order.mq.delay-ttl-ms 即可整体调整超时时长。
     */
    @Value("${order.mq.delay-ttl-ms:1800000}")
    private long delayTtlMs;

    // ==================== 拓扑声明：2 个交换机 + 2 个队列 + 2 条绑定 ====================

    /**
     * 业务延时交换机（direct 类型，持久化）。
     * direct 类型按路由键精确匹配，语义最直观，适合本场景一对一的固定路由。
     */
    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(ORDER_DELAY_EXCHANGE, true, false);
    }

    /**
     * 死信交换机（direct 类型，持久化）。
     * 延时队列过期消息的"中转站"，与延时交换机解耦，未来可挂更多死信队列。
     */
    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(ORDER_DLX_EXCHANGE, true, false);
    }

    /**
     * 业务延时队列（持久化，无消费者）。三个关键参数：
     * <ul>
     *   <li>x-message-ttl：队列级 TTL，消息存活上限（默认 30 分钟），到期即判定过期；</li>
     *   <li>x-dead-letter-exchange：过期消息转发到哪个交换机（死信交换机）；</li>
     *   <li>x-dead-letter-routing-key：转发时使用的新路由键（指向死信队列）。</li>
     * </ul>
     * 补充：若发送时又给单条消息设置了更短的 expiration，RabbitMQ 取"队列 TTL 与消息 TTL 较短者"，
     * 测试接口正是利用这一点发送短 TTL 消息实现秒级调试。
     */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                .withArgument("x-message-ttl", delayTtlMs)
                .withArgument("x-dead-letter-exchange", ORDER_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_DLX_ROUTING_KEY)
                .build();
    }

    /**
     * 死信队列（持久化）。接收从延时队列过期转发来的消息，
     * 由 OrderDeadLetterConsumer 以手动 ACK 模式消费。
     */
    @Bean
    public Queue orderDlxQueue() {
        return QueueBuilder.durable(ORDER_DLX_QUEUE).build();
    }

    /** 绑定：延时队列 ←(order.delay.key)← 延时交换机 */
    @Bean
    public Binding orderDelayBinding(Queue orderDelayQueue, DirectExchange orderDelayExchange) {
        return BindingBuilder.bind(orderDelayQueue).to(orderDelayExchange).with(ORDER_DELAY_ROUTING_KEY);
    }

    /** 绑定：死信队列 ←(order.dlx.key)← 死信交换机 */
    @Bean
    public Binding orderDlxBinding(Queue orderDlxQueue, DirectExchange orderDlxExchange) {
        return BindingBuilder.bind(orderDlxQueue).to(orderDlxExchange).with(ORDER_DLX_ROUTING_KEY);
    }

    // ==================== 消息体序列化与 RabbitTemplate 可靠投递回调 ====================

    /**
     * 消息体统一用 JSON 序列化（默认是 JDK 序列化，管理台里不可读且跨语言不友好）。
     * 该 Bean 会被 Spring Boot 自动装配到监听容器（消费者反序列化）。
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 自定义 RabbitTemplate，挂载两个可靠投递回调。
     *
     * <p>注意：一旦自定义 RabbitTemplate Bean，Spring Boot 对 RabbitTemplate 的自动配置
     * （RabbitTemplateConfiguration）会整体让位，因此 yml 中 spring.rabbitmq.template.* 的配置
     * （如 mandatory）不会再自动生效，必须在这里手动设置。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter);

        // mandatory=true：消息无法路由到任何队列时"退回"给生产者（触发 ReturnsCallback），
        // 而不是被 Broker 静默丢弃。与 yml 的 publisher-returns: true 配套使用。
        rabbitTemplate.setMandatory(true);

        // 回调一：发布方确认。消息投递到 Broker 后异步回调。
        // correlationData 由生产者发送时传入（本方案用订单号），用于定位是哪条消息。
        // ack=true 表示 Broker 已接收（对持久化队列+持久化消息即已落盘）；
        // ack=false 表示 Broker 内部处理失败（如队列满、磁盘异常），cause 为原因。
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            String correlationId = correlationData == null ? "unknown" : correlationData.getId();
            if (ack) {
                log.info("[MQ-Confirm] 延时关单消息已送达 Broker, correlationId={}", correlationId);
            } else {
                // nack 场景：订单已创建但延时消息未进入 Broker，该订单不会自动关闭。
                // 生产环境应落本地消息表/补偿任务兜底，本学习项目先记录 ERROR 便于排查。
                log.error("[MQ-Confirm] 延时关单消息投递失败(nack), correlationId={}, cause={}", correlationId, cause);
            }
        });

        // 回调二：消息退回。消息到达了 Broker，但根据交换机+路由键找不到任何绑定队列时触发
        // （通常是路由键写错或队列未声明）。生产环境同样需要告警+补偿。
        rabbitTemplate.setReturnsCallback(returned -> {
            Message message = returned.getMessage();
            log.error("[MQ-Return] 消息无法路由被退回, exchange={}, routingKey={}, replyCode={}, replyText={}, body={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText(),
                    new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8));
        });

        return rabbitTemplate;
    }
}
