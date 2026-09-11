package com.example.orderservice.controller;

import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.CursorPageResult;
import com.example.orderservice.dto.OrderCursorQuery;
import com.example.orderservice.dto.OrderPageQuery;
import com.example.orderservice.entity.OrderInfo;
import com.example.orderservice.mq.OrderDelayMessageProducer;
import com.example.orderservice.service.IdempotentTokenService;
import com.example.orderservice.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final IdempotentTokenService idempotentTokenService;

    public OrderController(OrderService orderService,
                           OrderDelayMessageProducer orderDelayMessageProducer,
                           IdempotentTokenService idempotentTokenService) {
        this.orderService = orderService;
        this.orderDelayMessageProducer = orderDelayMessageProducer;
        this.idempotentTokenService = idempotentTokenService;
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

    /**
     * 游标分页：以上一页最后一条记录的 id（lastId）续拉下一页，翻页成本恒定，
     * 适合"加载更多"式交互；不支持跳页、不返回总数。
     * 返回的 nextCursor 直接作为下一次请求的 lastId 传入，hasMore=false 表示已到末尾。
     */
    @GetMapping("/page/cursor")
    public ApiResponse<CursorPageResult<OrderInfo>> pageByCursor(@Valid OrderCursorQuery query) {
        return ApiResponse.success(orderService.pageOrdersByCursor(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderInfo> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(orderService.getOrderById(id));
    }

    /**
     * 下发一次性幂等令牌：前端进入下单页时领取，提交下单时通过
     * X-Idempotent-Token 请求头携带，服务端原子消费，防止重复下单。
     * 令牌 5 分钟有效，过期需重新领取。
     */
    @GetMapping("/idempotent-token")
    public ApiResponse<String> issueIdempotentToken() {
        return ApiResponse.success("Idempotent token issued", idempotentTokenService.issueToken());
    }

    @PostMapping
    public ApiResponse<OrderInfo> create(@Valid @RequestBody CreateOrderRequest request,
                                         @RequestHeader(value = "X-Idempotent-Token", required = false) String idempotentToken) {
        // 幂等第一关：令牌必须携带。缺失说明前端没走"先领号"流程，直接以 400 拦下
        if (!StringUtils.hasText(idempotentToken)) {
            throw new IllegalArgumentException(
                    "缺少幂等令牌，请先调用 GET /orders/idempotent-token 获取，并在请求头 X-Idempotent-Token 中携带");
        }

        // 幂等第二关：原子消费令牌。首次提交删除成功放行；重复提交/过期令牌在此时被拦（409）。
        // 注意消费必须在下单业务之前：若放在下单之后，两个并发请求可能都完成下单，幂等失效。
        // 已知权衡：若消费成功但 createOrder 因业务原因失败（如库存不足），令牌已被烧掉，
        // 客户端重试需重新领号 —— 生产上可让前端在收到业务失败后自动重新领号重试
        idempotentTokenService.consumeToken(idempotentToken);

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
