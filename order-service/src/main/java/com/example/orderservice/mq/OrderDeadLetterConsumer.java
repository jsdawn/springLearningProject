package com.example.orderservice.mq;

import com.example.orderservice.config.RabbitMqConfig;
import com.example.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 订单超时关闭死信消费者。
 *
 * <p>监听死信队列 order.dlx.queue：延时队列中过期（30 分钟未被支付）的消息会被 RabbitMQ
 * 自动转发到这里。消费逻辑：
 * <ol>
 *   <li>解析消息体（解析失败视为"毒消息"，记录日志后 ACK 丢弃，防止无限重投卡死队列）；</li>
 *   <li>调用 closeExpiredOrder：仅当订单仍为【待支付(1)】时关闭为【超时关闭(3)】并回补库存；
 *       订单不存在、已取消、已关闭等场景全部幂等跳过（消息重复投递也不会产生副作用）；</li>
 *   <li>手动 ACK：业务处理成功（含幂等跳过）→ basicAck 确认；
 *       抛出异常（DB/Feign 不可用等）→ basicNack(requeue=true) 让消息重投重试。</li>
 * </ol>
 *
 * <p>手动 ACK 说明：yml 中 acknowledge-mode: manual，Spring 不会自动确认，
 * 必须由本类显式调用 channel.basicAck / basicNack，这是"消费端防消息丢失"的关键。
 */
@Component
public class OrderDeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderDeadLetterConsumer.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public OrderDeadLetterConsumer(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    /**
     * 死信队列监听入口。
     *
     * <p>方法签名刻意接收原始 Message 而不是反序列化后的 DTO：
     * 这样解析异常可以由本方法自己兜底（ACK 丢弃毒消息），
     * 避免反序列化失败直接抛给容器默认错误处理器导致无脑重投。
     *
     * @param message 原始 AMQP 消息（JSON 消息体 + 消息属性）
     * @param channel 原生信道，用于手动 basicAck / basicNack
     */
    @RabbitListener(queues = RabbitMqConfig.ORDER_DLX_QUEUE)
    public void handleExpiredOrder(Message message, Channel channel) throws IOException {
        // 手动 ACK 必须使用当前消息的 deliveryTag（投递标签），多次批量确认时第二个参数为 false 表示只确认本条
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        // ---------- 第 1 步：解析消息体 ----------
        OrderDelayMessage payload;
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            payload = objectMapper.readValue(body, OrderDelayMessage.class);
            if (payload == null || payload.getOrderId() == null) {
                throw new IllegalArgumentException("orderId is missing in message body");
            }
        } catch (Exception e) {
            // 毒消息（格式错误/字段缺失）：重试也不可能成功，记录 ERROR 后 ACK 丢弃。
            // 生产环境可将此类消息转入独立的"异常消息归档队列"人工排查。
            log.error("[MQ-DLQ] 死信消息解析失败，ACK 丢弃防止无限重投, body={}",
                    new String(message.getBody(), StandardCharsets.UTF_8), e);
            channel.basicAck(deliveryTag, false);
            return;
        }

        // ---------- 第 2 步：执行超时关单（幂等） ----------
        try {
            boolean closed = orderService.closeExpiredOrder(payload.getOrderId());
            if (closed) {
                log.info("[MQ-DLQ] 订单超时未支付，已自动关闭并回补库存, orderId={}, orderNo={}",
                        payload.getOrderId(), payload.getOrderNo());
            } else {
                // 订单不存在 / 已取消 / 已关闭 / 已支付（未来扩展）：无需处理。
                // 重复投递、用户抢先手动取消等场景都会走到这里，幂等跳过即可。
                log.info("[MQ-DLQ] 订单无需关闭（不存在或非待支付状态），幂等跳过, orderId={}, orderNo={}",
                        payload.getOrderId(), payload.getOrderNo());
            }
            // 业务处理完成（含幂等跳过），手动确认，消息从队列移除
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 瞬时故障（数据库抖动、product-service 不可用等）：
            // basicNack(requeue=true) 让消息重新入队稍后重试。
            // 注意：若异常永远无法恢复（毒业务数据），会形成重投循环，
            // 生产环境应配合重试次数上限或"死信再死信"队列兜底，本学习项目暂不展开。
            log.error("[MQ-DLQ] 处理超时关单异常，消息重投重试, orderId={}, orderNo={}",
                    payload.getOrderId(), payload.getOrderNo(), e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
