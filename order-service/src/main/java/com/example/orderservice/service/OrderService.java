package com.example.orderservice.service;

import com.example.common.response.PageResult;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.CursorPageResult;
import com.example.orderservice.dto.OrderCursorQuery;
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
     * 偏移量较小走直接 LIMIT；偏移量达到深分页阈值后自动切换为延迟关联（子查询定位 id）。
     */
    PageResult<OrderInfo> pageOrders(OrderPageQuery query);

    /**
     * 订单游标分页查询（lastId 续拉，翻页成本恒定，不支持跳页）。
     */
    CursorPageResult<OrderInfo> pageOrdersByCursor(OrderCursorQuery query);
}
