package com.example.orderservice.mq;

import com.example.orderservice.config.RabbitMqConfig;
import com.example.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
 *       业务异常 → 带重试计数的有限次重投，超过上限则 ACK 丢弃并告警。</li>
 * </ol>
 *
 * <p>手动 ACK 说明：yml 中 acknowledge-mode: manual，Spring 不会自动确认，
 * 必须由本类显式调用 channel.basicAck / basicNack，这是"消费端防消息丢失"的关键。
 *
 * <p><b>为什么不用 basicNack(requeue=true) 做重试</b>：nack 只能把消息原样塞回队列，
 * 改不了消息头，重试次数无从记录，结果是"要么无限重投、要么永远不知道重试了几次"。
 * 本类改为"重新发布一条携带递增计数的新消息 + ACK 旧消息"，计数随消息头传递。
 * 重新发布的目标是<b>死信交换机</b>而非延时队列，这样重试立即生效，不必再等一个 TTL 周期。
 */
@Component
public class OrderDeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderDeadLetterConsumer.class);

    /**
     * 重试次数上限。达到后不再重投，ACK 丢弃并打 ERROR 告警。
     * 生产环境应把这类消息转入独立的"异常归档队列"由人工介入，本学习项目暂不引入该队列。
     */
    private static final int MAX_RETRY_COUNT = 3;

    /** 重试计数存放的消息头名称，随重新发布的消息一起传递 */
    private static final String RETRY_COUNT_HEADER = "x-retry-count";

    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    public OrderDeadLetterConsumer(OrderService orderService,
                                   ObjectMapper objectMapper,
                                   RabbitTemplate rabbitTemplate) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
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
            // 瞬时故障（数据库抖动、product-service 不可用等）：有限次重投重试。
            // 此时 closeExpiredOrder 上的 @GlobalTransactional 已保证"改订单状态"与"回补库存"
            // 一起回滚，订单仍是待支付，所以重投是安全的，不会重复回补库存。
            int retryCount = readRetryCount(message);
            if (retryCount >= MAX_RETRY_COUNT) {
                log.error("[MQ-DLQ] 超时关单重试已达上限 {} 次，停止重投并告警"
                                + "（生产应转归档队列人工处理）, orderId={}, orderNo={}",
                        MAX_RETRY_COUNT, payload.getOrderId(), payload.getOrderNo(), e);
                channel.basicAck(deliveryTag, false);
                return;
            }

            int nextRetryCount = retryCount + 1;
            try {
                republishWithRetryCount(message, nextRetryCount);
            } catch (Exception republishError) {
                // 重新发布失败（Broker 不可用等）：绝不能 ACK，否则消息永久丢失。
                // 退回 basicNack 重投兜底——虽然丢掉了计数，但至少保住消息不丢。
                log.error("[MQ-DLQ] 重试消息重新发布失败，退回 nack 重投, orderId={}, orderNo={}",
                        payload.getOrderId(), payload.getOrderNo(), republishError);
                channel.basicNack(deliveryTag, false, true);
                return;
            }
            log.warn("[MQ-DLQ] 处理超时关单异常，安排第 {} 次重试, orderId={}, orderNo={}",
                    nextRetryCount, payload.getOrderId(), payload.getOrderNo(), e);
            channel.basicAck(deliveryTag, false);
        }
    }

    /**
     * 读取消息头中的重试次数。RabbitMQ 传回的数值类型可能是 Integer / Long / String，统一兼容。
     */
    private int readRetryCount(Message message) {
        Object value = message.getMessageProperties().getHeader(RETRY_COUNT_HEADER);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 重新发布一条携带新重试计数的消息到死信交换机。
     *
     * <p>刻意新建 MessageProperties 而不是复用原消息的：原消息的属性里带着消费端特有的
     * deliveryTag、消费时间戳等，原样转发出去语义混乱。这里只保留投递属性（内容类型、持久化）。
     */
    private void republishWithRetryCount(Message message, int retryCount) {
        MessageProperties source = message.getMessageProperties();
        MessageProperties props = new MessageProperties();
        props.setContentType(source.getContentType());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setHeader(RETRY_COUNT_HEADER, retryCount);

        rabbitTemplate.send(RabbitMqConfig.ORDER_DLX_EXCHANGE,
                RabbitMqConfig.ORDER_DLX_ROUTING_KEY,
                new Message(message.getBody(), props));
    }
}
