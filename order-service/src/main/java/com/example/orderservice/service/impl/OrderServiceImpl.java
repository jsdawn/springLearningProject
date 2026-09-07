package com.example.orderservice.service.impl;

import com.example.common.context.LoginUserHolder;
import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
import com.example.orderservice.client.ProductSummary;
import com.example.orderservice.client.UserSummary;
import com.example.orderservice.dto.CreateOrderItemRequest;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderPageQuery;
import com.example.orderservice.entity.OrderInfo;
import com.example.orderservice.entity.OrderItem;
import com.example.orderservice.feign.ProductFeignClient;
import com.example.orderservice.feign.UserFeignClient;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.seata.spring.annotation.GlobalTransactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final Integer ORDER_STATUS_CREATED = 1;
    private static final Integer ORDER_STATUS_CANCELLED = 2;
    /** 超时关闭：下单后 30 分钟未支付，由死信消费者自动关闭（区别于用户主动取消的 2） */
    private static final Integer ORDER_STATUS_TIMEOUT_CLOSED = 3;
    private static final Integer USER_STATUS_ENABLED = 1;
    private static final Integer PRODUCT_STATUS_ON_SALE = 1;
    private static final DateTimeFormatter ORDER_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final UserFeignClient userFeignClient;
    private final ProductFeignClient productFeignClient;

    public OrderServiceImpl(OrderMapper orderMapper,
                            UserFeignClient userFeignClient,
                            ProductFeignClient productFeignClient) {
        this.orderMapper = orderMapper;
        this.userFeignClient = userFeignClient;
        this.productFeignClient = productFeignClient;
    }

    @Override
    public List<OrderInfo> listOrders(String orderNo, Long userId) {
        List<OrderInfo> orders = orderMapper.findAll(orderNo, userId);
        fillOrderItems(orders);
        return orders;
    }

    @Override
    public PageResult<OrderInfo> pageOrders(OrderPageQuery query) {
        int pageNum = query == null || query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query == null || query.getPageSize() == null ? 10 : query.getPageSize();
        if (pageNum <= 0) {
            pageNum = 1;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }

        int offset = (pageNum - 1) * pageSize;
        String orderNo = query == null ? null : query.getOrderNo();
        Long userId = query == null ? null : query.getUserId();

        long total = orderMapper.countByCondition(orderNo, userId);
        if (total <= 0) {
            return PageResult.empty(pageNum, pageSize);
        }

        List<OrderInfo> orders = orderMapper.findPageByCondition(orderNo, userId, offset, pageSize);
        fillOrderItems(orders);
        return PageResult.of(orders, total, pageNum, pageSize);
    }

    @Override
    public OrderInfo getOrderById(Long id) {
        OrderInfo orderInfo = orderMapper.findById(id);
        if (orderInfo == null) {
            throw new IllegalArgumentException("Order not found, id=" + id);
        }
        orderInfo.setItems(orderMapper.findItemsByOrderId(id));
        return orderInfo;
    }

    /**
     * 创建订单。这是全链路的全局事务边界（TM 角色）：
     * 本服务的订单入库 + product-service 的扣库存，被 Seata 编排成同一全局事务下的两个分支事务。
     * 任一环节失败，TC 通知各 RM 依据 undo_log 反向补偿，库存自动回补，业务代码无需干预。
     *
     * <p>两个注解的分工：
     * <ul>
     *   <li>{@code @GlobalTransactional}：TM 角色，向 TC 申请 XID，并通过 Feign 把 XID 传给下游服务
     *   <li>{@code @Transactional}：本地事务边界，订单主表+明细的入库作为<b>一个</b>分支事务整体提交
     * </ul>
     * 去掉 {@code @Transactional} 会导致每条 SQL 各自 autocommit、各自注册分支，
     * 既低效又把"主表+明细"的原子性拆散，所以这里两个注解都要保留。
     */
    @Override
    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    @Transactional
    public OrderInfo createOrder(CreateOrderRequest request) {
        // 先校验下单请求本身是否合法，比如商品列表、购买数量是否缺失（userId 不再校验，
        // 改由下文 LoginUserHolder 强制覆盖——避免客户端伪造身份下单）。
        validateCreateRequest(request);

        // 鉴权闭环：以网关透传的登录用户为准，覆盖客户端传的 userId。
        // 客户端即使伪造了 X-Auth-* Header 也会被网关清洗，且此处未登录直接 401。
        request.setUserId(LoginUserHolder.requireUserIdAsLong());

        // 订单服务不自己维护用户主数据，所以先远程调用 user-service 校验用户是否存在且可用。
        UserSummary user = getUserById(request.getUserId());
        if (!USER_STATUS_ENABLED.equals(user.getStatus())) {
            throw new IllegalStateException("User is disabled, userId=" + request.getUserId());
        }

        // orderItems 用来暂存待落库的订单明细，totalAmount 累加订单总金额。
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CreateOrderItemRequest itemRequest : request.getItems()) {
            // 逐个商品校验：是否存在、是否上架、库存是否充足。
            ProductSummary product = getProductById(itemRequest.getProductId());
            validateProductForOrder(product, itemRequest.getQuantity());

            // 远程扣减库存：product-service 作为 RM 注册分支事务并写 undo_log。
            // 后续任一步失败（含订单写库失败），TC 会反向补偿这次扣减，库存自动回补。
            adjustProductStock(product.getId(), -itemRequest.getQuantity());

            // 把商品快照写进订单明细，避免后续商品名称或价格变化影响历史订单。
            OrderItem orderItem = buildOrderItem(product, itemRequest.getQuantity());
            orderItems.add(orderItem);
            totalAmount = totalAmount.add(orderItem.getAmount());
        }

        // 构造订单主表数据。
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderNo(generateOrderNo());
        orderInfo.setUserId(request.getUserId());
        orderInfo.setTotalAmount(totalAmount);
        orderInfo.setStatus(ORDER_STATUS_CREATED);

        // 先保存订单主表，拿到数据库生成的订单 ID。
        int orderRows = orderMapper.insert(orderInfo);
        if (orderRows <= 0 || orderInfo.getId() == null) {
            throw new IllegalStateException("Create order failed");
        }

        // 把刚生成的订单 ID 回填到每条订单明细里，建立主从关系。
        for (OrderItem orderItem : orderItems) {
            orderItem.setOrderId(orderInfo.getId());
        }

        // 再批量保存订单明细，如果保存条数不一致，说明创建不完整，抛异常触发全局回滚。
        int itemRows = orderMapper.batchInsertItems(orderItems);
        if (itemRows != orderItems.size()) {
            throw new IllegalStateException("Create order items failed, orderId=" + orderInfo.getId());
        }

        // 最后重新查一次数据库，返回包含主表和明细的最新订单结果。
        return getOrderById(orderInfo.getId());
    }

    @Override
    @Transactional
    public OrderInfo cancelOrder(Long id) {
        OrderInfo orderInfo = getOrderById(id);
        if (ORDER_STATUS_CANCELLED.equals(orderInfo.getStatus())) {
            throw new IllegalStateException("Order already cancelled, id=" + id);
        }

        int rows = orderMapper.updateStatusById(id, ORDER_STATUS_CANCELLED);
        if (rows <= 0) {
            throw new IllegalStateException("Cancel order failed, id=" + id);
        }
        return getOrderById(id);
    }

    @Override
    @Transactional
    public boolean closeExpiredOrder(Long id) {
        // 先查订单当前状态。订单不存在、或已经不是【待支付】（可能已支付/已取消/已关闭），
        // 都直接返回 false 幂等跳过，保证死信消息重复投递、或用户抢先支付等场景不产生副作用。
        OrderInfo orderInfo = orderMapper.findById(id);
        if (orderInfo == null) {
            log.info("closeExpiredOrder skipped, order not found, id={}", id);
            return false;
        }
        if (!ORDER_STATUS_CREATED.equals(orderInfo.getStatus())) {
            log.info("closeExpiredOrder skipped, order not pending-pay, id={}, status={}",
                    id, orderInfo.getStatus());
            return false;
        }

        // 用"从 1 到 3"的条件更新做乐观锁：只有当前仍是待支付才会更新成功。
        // 若并发的支付/取消已把状态改掉，这里影响行数为 0，同样幂等跳过，不会误关。
        int rows = orderMapper.updateStatusFromTo(id, ORDER_STATUS_CREATED, ORDER_STATUS_TIMEOUT_CLOSED);
        if (rows <= 0) {
            log.info("closeExpiredOrder skipped by concurrent update, id={}", id);
            return false;
        }

        // 关单成功后回补库存：把该订单占用的库存逐条加回商品服务。
        // 注意：关单由 MQ 消费者触发，是独立于下单的链路，未纳入 Seata 全局事务，
        // 所以这里仍沿用"尽力而为"的单条回补（单条失败不影响其余条目），与下单链路的强一致回滚不同。
        List<OrderItem> items = orderMapper.findItemsByOrderId(id);
        rollbackStockForItems(items);

        log.info("closeExpiredOrder success, order closed and stock restored, id={}, orderNo={}",
                id, orderInfo.getOrderNo());
        return true;
    }

    /**
     * 按订单明细把库存加回商品服务（尽力而为，单条失败不影响其余条目）。
     */
    private void rollbackStockForItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (OrderItem item : items) {
            try {
                adjustProductStock(item.getProductId(), item.getQuantity());
            } catch (RuntimeException e) {
                // 回补失败仅记录日志，不阻断关单主流程（与下单失败回补的处理方式一致）。
                log.error("Restore stock failed on closeExpiredOrder, productId={}, quantity={}",
                        item.getProductId(), item.getQuantity(), e);
            }
        }
    }

    private void validateCreateRequest(CreateOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        // userId 不再校验非空：客户端传入的 userId 会被登录上下文覆盖，未登录会直接抛 401。
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order items are required");
        }

        for (CreateOrderItemRequest item : request.getItems()) {
            if (item.getProductId() == null) {
                throw new IllegalArgumentException("productId is required");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("quantity must be greater than 0");
            }
        }
    }

    private void validateProductForOrder(ProductSummary product, Integer quantity) {
        if (!PRODUCT_STATUS_ON_SALE.equals(product.getStatus())) {
            throw new IllegalStateException("Product is off sale, productId=" + product.getId());
        }
        if (product.getStock() == null || product.getStock() < quantity) {
            throw new IllegalStateException("Insufficient stock, productId=" + product.getId());
        }
        if (product.getPrice() == null) {
            throw new IllegalStateException("Product price is missing, productId=" + product.getId());
        }
    }

    private OrderItem buildOrderItem(ProductSummary product, Integer quantity) {
        OrderItem orderItem = new OrderItem();
        orderItem.setProductId(product.getId());
        orderItem.setProductName(product.getProductName());
        orderItem.setProductPrice(product.getPrice());
        orderItem.setQuantity(quantity);
        orderItem.setAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return orderItem;
    }

    private String generateOrderNo() {
        return "ORD" + LocalDateTime.now().format(ORDER_NO_TIME_FORMATTER) + Math.abs(System.nanoTime() % 1000);
    }

    private UserSummary getUserById(Long userId) {
        return extractRemoteData(userFeignClient.getUserById(userId), "User");
    }

    private ProductSummary getProductById(Long productId) {
        return extractRemoteData(productFeignClient.getProductById(productId), "Product");
    }

    private void adjustProductStock(Long productId, Integer delta) {
        extractRemoteData(productFeignClient.adjustStock(productId, delta), "Product");
    }

    private <T> T extractRemoteData(ApiResponse<T> response, String resourceName) {
        if (response == null) {
            throw new IllegalStateException(resourceName + " service returned empty response");
        }
        if (response.getCode() == null || response.getCode() != 200) {
            throw new IllegalStateException(response.getMessage());
        }
        if (response.getData() == null) {
            throw new IllegalArgumentException(resourceName + " not found");
        }
        return response.getData();
    }

    private void fillOrderItems(List<OrderInfo> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }

        List<Long> orderIds = new ArrayList<>();
        for (OrderInfo order : orders) {
            orderIds.add(order.getId());
            order.setItems(new ArrayList<OrderItem>());
        }

        List<OrderItem> items = orderMapper.findItemsByOrderIds(orderIds);
        Map<Long, List<OrderItem>> itemsByOrderId = new HashMap<>();
        for (OrderItem item : items) {
            if (!itemsByOrderId.containsKey(item.getOrderId())) {
                itemsByOrderId.put(item.getOrderId(), new ArrayList<OrderItem>());
            }
            itemsByOrderId.get(item.getOrderId()).add(item);
        }

        for (OrderInfo order : orders) {
            List<OrderItem> orderItems = itemsByOrderId.get(order.getId());
            if (orderItems != null) {
                order.setItems(orderItems);
            }
        }
    }
}
