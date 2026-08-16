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
     * 订单分页查询（手写 LIMIT 分页，便于理解 MyBatis 分页实现）。
     */
    PageResult<OrderInfo> pageOrders(OrderPageQuery query);
}
