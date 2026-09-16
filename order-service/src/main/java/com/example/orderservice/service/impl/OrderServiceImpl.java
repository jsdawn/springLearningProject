package com.example.orderservice.service.impl;

import com.example.common.context.LoginUserHolder;
import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
import com.example.orderservice.client.ProductSummary;
import com.example.orderservice.client.UserSummary;
import com.example.orderservice.config.OrderBusinessProperties;
import com.example.orderservice.dto.CreateOrderItemRequest;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.CursorPageResult;
import com.example.orderservice.dto.OrderCursorQuery;
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
    /** 业务参数来自 Nacos 配置中心（order-service.yaml），改值动态生效无需重启 */
    private final OrderBusinessProperties orderBusinessProperties;

    public OrderServiceImpl(OrderMapper orderMapper,
                            UserFeignClient userFeignClient,
                            ProductFeignClient productFeignClient,
                            OrderBusinessProperties orderBusinessProperties) {
        this.orderMapper = orderMapper;
        this.userFeignClient = userFeignClient;
        this.productFeignClient = productFeignClient;
        this.orderBusinessProperties = orderBusinessProperties;
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
        Integer status = query == null ? null : query.getStatus();

        long total = orderMapper.countByCondition(orderNo, userId, status);
        if (total <= 0) {
            return PageResult.empty(pageNum, pageSize);
        }

        // 深分页优化：大偏移改走延迟关联——内层子查询只扫覆盖索引拿 id（不回表），
        // 外层按主键精确取整行，避免"组装 offset+size 行再丢弃 offset 行"的浪费。
        // 阈值来自 Nacos 配置中心（order.business.deep-page-offset-threshold），动态可调
        List<OrderInfo> orders = offset >= orderBusinessProperties.getDeepPageOffsetThreshold()
                ? orderMapper.findPageByConditionDeferred(orderNo, userId, status, offset, pageSize)
                : orderMapper.findPageByCondition(orderNo, userId, status, offset, pageSize);
        fillOrderItems(orders);
        return PageResult.of(orders, total, pageNum, pageSize);
    }

    /**
     * 游标分页：WHERE id &gt; lastId LIMIT size，B+ 树直接定位后续拉，
     * 任意页成本恒定 O(size)，彻底消除 LIMIT 偏移量问题。
     *
     * <p>探测下一页的常用技巧：按 pageSize + 1 查询——
     * 返回条数超过 pageSize 说明后面还有数据（hasMore），多查的那条丢弃即可，
     * 避免"查两趟"或为探测再发一条 COUNT。
     */
    @Override
    public CursorPageResult<OrderInfo> pageOrdersByCursor(OrderCursorQuery query) {
        int pageSize = query == null || query.getPageSize() == null || query.getPageSize() <= 0
                ? 10 : query.getPageSize();
        long lastId = query == null || query.getLastId() == null ? 0L : query.getLastId();
        String orderNo = query == null ? null : query.getOrderNo();
        Long userId = query == null ? null : query.getUserId();
        Integer status = query == null ? null : query.getStatus();

        List<OrderInfo> fetched = orderMapper.findPageByCursor(orderNo, userId, status, lastId, pageSize + 1);
        boolean hasMore = fetched.size() > pageSize;
        if (hasMore) {
            fetched = fetched.subList(0, pageSize);
        }

        if (fetched.isEmpty()) {
            return CursorPageResult.empty();
        }
        fillOrderItems(fetched);
        Long nextCursor = fetched.get(fetched.size() - 1).getId();
        return new CursorPageResult<>(fetched, nextCursor, hasMore);
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
    @GlobalTransactional(name = "close-expired-order", rollbackFor = Exception.class)
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
        // 已纳入 Seata 全局事务（见本方法上的 @GlobalTransactional）：
        // 任一条回补失败 → 异常冒泡 → 全局回滚 → 订单状态退回【待支付】，由 MQ 重投再次尝试。
        // 这样"订单置为超时关闭"与"库存回补"是原子的，不会出现旧实现里
        // 订单已关闭但库存没回补、且消息已被 ACK 永不重试的库存泄漏。
        List<OrderItem> items = orderMapper.findItemsByOrderId(id);
        rollbackStockForItems(items);

        log.info("closeExpiredOrder success, order closed and stock restored, id={}, orderNo={}",
                id, orderInfo.getOrderNo());
        return true;
    }

    /**
     * 按订单明细把库存加回商品服务。
     *
     * <p>刻意<b>不</b>逐条 catch 异常：任一条回补失败都必须冒泡，交给
     * {@code @GlobalTransactional} 触发全局回滚，让"订单状态置为超时关闭"与"库存回补"
     * 成为一个原子操作。
     *
     * <p>旧实现在这里吞掉异常，后果是：订单已关闭、库存没回补，而消息照常被 ACK，
     * 永远不会重试——这部分库存在系统里永久泄漏，且没有任何报错。
     */
    private void rollbackStockForItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (OrderItem item : items) {
            adjustProductStock(item.getProductId(), item.getQuantity());
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
