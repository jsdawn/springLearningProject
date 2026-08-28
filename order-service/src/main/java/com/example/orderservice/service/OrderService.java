package com.example.orderservice.service;

import com.example.common.response.PageResult;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderPageQuery;
import com.example.orderservice.entity.OrderInfo;

import java.util.List;

public interface OrderService {

    List<OrderInfo> listOrders(String orderNo, Long userId);

    OrderInfo getOrderById(Long id);

    OrderInfo createOrder(CreateOrderRequest request);

    OrderInfo cancelOrder(Long id);

    /**
     * 订单超时关闭（由死信消费者调用，幂等）。
     * <p>
     * 仅当订单仍处于【待支付】状态时，才将其置为【超时关闭】并回补库存；
     * 订单不存在、已取消、已关闭等非待支付场景一律直接跳过，保证消息重复投递不产生副作用。
     *
     * @param id 订单ID
     * @return true 表示本次确实执行了关闭；false 表示无需关闭（幂等跳过）
     */
    boolean closeExpiredOrder(Long id);

    /**
     * 订单分页查询（手写 LIMIT 分页，便于理解 MyBatis 分页实现）。
     */
    PageResult<OrderInfo> pageOrders(OrderPageQuery query);
}
