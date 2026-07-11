package com.example.orderservice.service;

import com.example.orderservice.entity.OrderInfo;

import java.util.List;

public interface OrderService {

    List<OrderInfo> listOrders();
}
