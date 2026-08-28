package com.example.orderservice.mq;

import com.example.orderservice.config.RabbitMqConfig;
import com.example.orderservice.entity.OrderInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/**
 * 订单超时关闭延时消息生产者。
 *
 * <p>职责单一：只负责把"下单成功"事件封装成延时消息投递到延时队列，
 * 不关心订单业务本身（下单事务由 OrderServiceImpl 独立完成，消息发送在事务提交之后）。
 *
 * <p>投递可靠性：
 * <ul>
 *   <li>发送时携带 CorrelationData（订单号），与 RabbitMqConfig 中的 ConfirmCallback 对应，
 *       可在回调日志里精确定位到是哪笔订单的消息成功/失败；</li>
 *   <li>消息级 TTL（expiration）：默认与队列 TTL 相同（30 分钟）；
 *       测试接口可传入更短的 TTL，RabbitMQ 对"队列 TTL 与消息 TTL 并存"取较短者生效，
 *       从而实现秒级调试，无需真等 30 分钟。</li>
 * </ul>
 */
@Component
public class OrderDelayMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderDelayMessageProducer.class);

    private final RabbitTemplate rabbitTemplate;

    /** 默认延时时长（毫秒），与延时队列的 x-message-ttl 保持同一配置源 */
    @Value("${order.mq.delay-ttl-ms:1800000}")
    private long defaultDelayTtlMs;

    public OrderDelayMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发送订单超时关闭延时消息（使用默认 30 分钟延时）。
     * 下单成功后由 OrderController 调用。
     *
     * @param order 已创建成功（事务已提交）的订单
     */
    public void sendOrderCloseDelay(OrderInfo order) {
        sendOrderCloseDelay(order, defaultDelayTtlMs);
    }

    /**
     * 发送订单超时关闭延时消息（可指定延时毫秒数，供测试接口短延时调试）。
     *
     * @param order 订单（必须已落库）
     * @param ttlMs 本次消息的延时毫秒数，必须大于 0
     */
    public void sendOrderCloseDelay(OrderInfo order, long ttlMs) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Order must be persisted before sending delay message");
        }
        if (ttlMs <= 0) {
            throw new IllegalArgumentException("ttlMs must be greater than 0");
        }

        // 消息体只带定位信息，消费时以数据库实时状态为准
        long createTimeMillis = order.getCreateTime() == null
                ? System.currentTimeMillis()
                : order.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        OrderDelayMessage payload = new OrderDelayMessage(
                order.getId(), order.getOrderNo(), order.getUserId(), createTimeMillis);

        // CorrelationData 用订单号做关联ID：ConfirmCallback 里可据此定位具体订单
        CorrelationData correlationData = new CorrelationData(order.getOrderNo());

        // MessagePostProcessor：在消息真正发出前补充消息属性
        MessagePostProcessor postProcessor = message -> {
            // 消息级 TTL（单位毫秒字符串）。与队列级 x-message-ttl 并存时，RabbitMQ 取较短者：
            // 正常下单传 30 分钟（与队列一致）；测试接口传十几秒即可快速看到死信消费效果
            message.getMessageProperties().setExpiration(String.valueOf(ttlMs));
            // messageId 也设为订单号，管理台（15672）里可直接肉眼对应到订单
            message.getMessageProperties().setMessageId(order.getOrderNo());
            return message;
        };

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.ORDER_DELAY_EXCHANGE,
                RabbitMqConfig.ORDER_DELAY_ROUTING_KEY,
                payload,
                postProcessor,
                correlationData);

        log.info("[MQ-Send] 已发送订单超时关闭延时消息, orderNo={}, orderId={}, ttlMs={}",
                order.getOrderNo(), order.getId(), ttlMs);
    }
}
