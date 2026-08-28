package com.example.orderservice.controller;

import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderPageQuery;
import com.example.orderservice.entity.OrderInfo;
import com.example.orderservice.mq.OrderDelayMessageProducer;
import com.example.orderservice.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/orders")
@Validated
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final OrderDelayMessageProducer orderDelayMessageProducer;

    public OrderController(OrderService orderService,
                           OrderDelayMessageProducer orderDelayMessageProducer) {
        this.orderService = orderService;
        this.orderDelayMessageProducer = orderDelayMessageProducer;
    }

    @GetMapping
    public ApiResponse<List<OrderInfo>> list(@RequestParam(value = "orderNo", required = false) String orderNo,
                                             @RequestParam(value = "userId", required = false) Long userId) {
        return ApiResponse.success(orderService.listOrders(orderNo, userId));
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<OrderInfo>> page(@Valid OrderPageQuery query) {
        return ApiResponse.success(orderService.pageOrders(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderInfo> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(orderService.getOrderById(id));
    }

    @PostMapping
    public ApiResponse<OrderInfo> create(@Valid @RequestBody CreateOrderRequest request) {
        // 原有下单业务不变：createOrder 内部完成校验、扣库存、落库，且自带 @Transactional。
        // 方法返回即代表下单事务已提交，此后再发延时消息，避免"事务回滚但消息已发出"的不一致。
        OrderInfo createdOrder = orderService.createOrder(request);

        // 下单成功后发送"超时自动关单"延时消息（30 分钟）。
        // 发送失败只记日志、不影响下单结果：订单已落库，消息丢失的最坏后果是该单不会被自动关闭，
        // 生产环境可用本地消息表/兜底扫描补偿，本学习项目不展开。
        try {
            orderDelayMessageProducer.sendOrderCloseDelay(createdOrder);
        } catch (RuntimeException e) {
            log.error("Send order-close delay message failed, orderNo={}", createdOrder.getOrderNo(), e);
        }

        return ApiResponse.success("Order created successfully", createdOrder);
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<OrderInfo> cancel(@PathVariable("id") Long id) {
        return ApiResponse.success("Order cancelled successfully", orderService.cancelOrder(id));
    }
}
