package com.example.orderservice.service;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.entity.OrderInfo;

import java.util.List;

public interface OrderService {

    List<OrderInfo> listOrders(String orderNo, Long userId);

    OrderInfo getOrderById(Long id);

    OrderInfo createOrder(CreateOrderRequest request);

    OrderInfo cancelOrder(Long id);
}
