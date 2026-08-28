package com.example.orderservice.mq;

/**
 * 订单超时关闭延时消息体。
 *
 * <p>下单成功后由 OrderDelayMessageProducer 发送到延时队列，30 分钟后过期进入死信队列，
 * 由 OrderDeadLetterConsumer 消费。消息只携带"定位订单"所需的最小字段集，
 * 关单时的订单状态、明细等一律以消费时刻的数据库实时数据为准（避免用过期快照做业务判断）。
 *
 * <p>字段说明：
 * <ul>
 *   <li>orderId：订单主键，消费者关单与回补库存的核心依据；</li>
 *   <li>orderNo：订单号，同时用作消息的 messageId / correlationId，便于全链路日志追踪；</li>
 *   <li>userId：下单用户，仅用于日志排查；</li>
 *   <li>createTimeMillis：下单时间戳（毫秒）。刻意不用 LocalDateTime，
 *       避免 JSON 序列化器对 Java8 时间类型的格式差异问题。</li>
 * </ul>
 */
public class OrderDelayMessage {

    /** 订单主键ID */
    private Long orderId;

    /** 订单号（业务唯一编号） */
    private String orderNo;

    /** 下单用户ID */
    private Long userId;

    /** 下单时间（epoch 毫秒） */
    private Long createTimeMillis;

    public OrderDelayMessage() {
    }

    public OrderDelayMessage(Long orderId, String orderNo, Long userId, Long createTimeMillis) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.createTimeMillis = createTimeMillis;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getCreateTimeMillis() {
        return createTimeMillis;
    }

    public void setCreateTimeMillis(Long createTimeMillis) {
        this.createTimeMillis = createTimeMillis;
    }

    @Override
    public String toString() {
        return "OrderDelayMessage{orderId=" + orderId
                + ", orderNo='" + orderNo + '\''
                + ", userId=" + userId
                + ", createTimeMillis=" + createTimeMillis + '}';
    }
}
