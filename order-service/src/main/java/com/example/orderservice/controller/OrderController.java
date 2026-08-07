package com.example.orderservice.controller;

import com.example.common.response.ApiResponse;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.entity.OrderInfo;
import com.example.orderservice.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<List<OrderInfo>> list(@RequestParam(value = "orderNo", required = false) String orderNo,
                                             @RequestParam(value = "userId", required = false) Long userId) {
        return ApiResponse.success(orderService.listOrders(orderNo, userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderInfo> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(orderService.getOrderById(id));
    }

    @PostMapping
    public ApiResponse<OrderInfo> create(@RequestBody CreateOrderRequest request) {
        return ApiResponse.success("Order created successfully", orderService.createOrder(request));
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<OrderInfo> cancel(@PathVariable("id") Long id) {
        return ApiResponse.success("Order cancelled successfully", orderService.cancelOrder(id));
    }
}
