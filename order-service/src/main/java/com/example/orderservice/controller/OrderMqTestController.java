package com.example.orderservice.controller;

import com.example.common.response.ApiResponse;
import com.example.orderservice.entity.OrderInfo;
import com.example.orderservice.mq.OrderDelayMessageProducer;
import com.example.orderservice.service.OrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RabbitMQ 死信延时关单——调试测试接口（仅用于开发联调，非业务接口）。
 *
 * <p>为什么需要它：默认延时是 30 分钟，走真实下单流程验证一次要等半小时，调试效率太低。
 * 这里提供两个快捷入口，配合短 TTL（如 15 秒）即可秒级观察完整链路：
 * 延时队列 → 过期 → 死信队列 → 消费者关单 + 回补库存。
 *
 * <p>典型调试步骤（详见 docs/development/RabbitMQ死信延时关单集成说明.md）：
 * <ol>
 *   <li>POST /orders 正常下一单，记下返回的订单 id；</li>
 *   <li>POST /order-mq-test/delay/{orderId}?ttlSeconds=15 发一条 15 秒延时消息；</li>
 *   <li>15 秒后 GET /orders/{orderId}，status 变为 3（超时关闭），同时商品库存已回补。</li>
 * </ol>
 */
@RestController
@RequestMapping("/order-mq-test")
public class OrderMqTestController {

    /** 未显式指定延时时的兜底值：30 分钟（与队列 TTL、生产者默认值保持一致） */
    private static final long DEFAULT_TTL_MS = 30 * 60 * 1000L;

    private final OrderService orderService;
    private final OrderDelayMessageProducer orderDelayMessageProducer;

    public OrderMqTestController(OrderService orderService,
                                 OrderDelayMessageProducer orderDelayMessageProducer) {
        this.orderService = orderService;
        this.orderDelayMessageProducer = orderDelayMessageProducer;
    }

    /**
     * 对一笔真实存在的订单发送超时关单延时消息（可指定短延时，快速验证）。
     *
     * <p>示例：POST /order-mq-test/delay/1?ttlSeconds=15
     * 表示"订单 1 将在 15 秒后走超时关单逻辑"。不传 ttlSeconds 时使用默认 30 分钟。
     *
     * @param orderId    订单ID（必须已存在，否则按订单不存在抛错）
     * @param ttlSeconds 延时秒数，可选；传入时必须大于 0
     */
    @PostMapping("/delay/{orderId}")
    public ApiResponse<String> sendDelayMessageForOrder(
            @PathVariable("orderId") Long orderId,
            @RequestParam(value = "ttlSeconds", required = false) Long ttlSeconds) {
        // 先确认订单真实存在，顺便取出订单号等信息构造消息
        OrderInfo order = orderService.getOrderById(orderId);

        long ttlMs;
        if (ttlSeconds == null) {
            // 未指定延时：走生产者默认值（= 队列 TTL，默认 30 分钟）
            orderDelayMessageProducer.sendOrderCloseDelay(order);
            ttlMs = DEFAULT_TTL_MS;
        } else {
            ttlMs = requirePositiveSeconds(ttlSeconds) * 1000L;
            orderDelayMessageProducer.sendOrderCloseDelay(order, ttlMs);
        }

        return ApiResponse.success("延时消息已发送, 订单号=" + order.getOrderNo()
                + ", 延时=" + (ttlMs / 1000) + "秒, 到期后若仍为待支付将自动关闭(状态1→3)并回补库存");
    }

    /**
     * 发送一条"模拟订单"的延时消息（不校验订单是否存在），用于验证消费者的幂等跳过分支。
     *
     * <p>示例：POST /order-mq-test/delay-mock?orderId=999999&ttlSeconds=10
     * 10 秒后消息进入死信队列，消费者发现订单不存在，打印日志并 ACK 丢弃——
     * 可在 order-service 日志中搜索 "[MQ-DLQ] 订单无需关闭" 观察该分支。
     *
     * @param orderId    任意订单ID（建议用一个不存在的，如 999999）
     * @param ttlSeconds 延时秒数，可选；传入时必须大于 0
     */
    @PostMapping("/delay-mock")
    public ApiResponse<String> sendMockDelayMessage(
            @RequestParam("orderId") Long orderId,
            @RequestParam(value = "ttlSeconds", required = false) Long ttlSeconds) {
        long ttlMs = ttlSeconds == null ? DEFAULT_TTL_MS : requirePositiveSeconds(ttlSeconds) * 1000L;

        // 直接手工构造消息体，绕过订单存在性校验，专门测试消费端"订单不存在/非待支付"的跳过分支
        OrderInfo mockOrder = new OrderInfo();
        mockOrder.setId(orderId);
        mockOrder.setOrderNo("MOCK-" + orderId);
        mockOrder.setUserId(0L);
        orderDelayMessageProducer.sendOrderCloseDelay(mockOrder, ttlMs);

        return ApiResponse.success("模拟延时消息已发送, 订单ID=" + orderId
                + ", 延时=" + (ttlMs / 1000) + "秒, 用于观察消费者幂等跳过分支(日志关键字 [MQ-DLQ])");
    }

    /**
     * 校验延时秒数必须为正数，返回原值便于链式换算成毫秒。
     */
    private long requirePositiveSeconds(long ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be greater than 0");
        }
        return ttlSeconds;
    }
}
