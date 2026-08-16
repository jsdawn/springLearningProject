package com.example.orderservice.service.impl;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Integer ORDER_STATUS_CREATED = 1;
    private static final Integer ORDER_STATUS_CANCELLED = 2;
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

    @Override
    @Transactional
    public OrderInfo createOrder(CreateOrderRequest request) {
        // 先校验下单请求本身是否合法，比如 userId、商品列表、购买数量是否缺失。
        validateCreateRequest(request);

        // 订单服务不自己维护用户主数据，所以先远程调用 user-service 校验用户是否存在且可用。
        UserSummary user = getUserById(request.getUserId());
        if (!USER_STATUS_ENABLED.equals(user.getStatus())) {
            throw new IllegalStateException("User is disabled, userId=" + request.getUserId());
        }

        // orderItems 用来暂存待落库的订单明细，totalAmount 累加订单总金额。
        // deductedItems 记录已经成功扣减过库存的商品，后面如果失败可以尽量回补库存。
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<CreateOrderItemRequest> deductedItems = new ArrayList<>();

        try {
            for (CreateOrderItemRequest itemRequest : request.getItems()) {
                // 逐个商品校验：是否存在、是否上架、库存是否充足。
                ProductSummary product = getProductById(itemRequest.getProductId());
                validateProductForOrder(product, itemRequest.getQuantity());

                // 先调用商品服务扣减库存，扣减成功后把这条记录记下来，便于异常时回滚。
                adjustProductStock(product.getId(), -itemRequest.getQuantity());
                deductedItems.add(itemRequest);

                // 把商品快照写进订单明细，避免后续商品名称或价格变化影响历史订单。
                OrderItem orderItem = buildOrderItem(product, itemRequest.getQuantity());
                orderItems.add(orderItem);
                totalAmount = totalAmount.add(orderItem.getAmount());
            }

            // 先构造订单主表数据。
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

            // 再批量保存订单明细，如果保存条数不一致，说明创建不完整，直接抛异常。
            int itemRows = orderMapper.batchInsertItems(orderItems);
            if (itemRows != orderItems.size()) {
                throw new IllegalStateException("Create order items failed, orderId=" + orderInfo.getId());
            }

            // 最后重新查一次数据库，返回包含主表和明细的最新订单结果。
            return getOrderById(orderInfo.getId());
        } catch (RuntimeException e) {
            // 当前阶段还没有分布式事务，所以这里做一次“尽力而为”的库存回补。
            rollbackAdjustedStock(deductedItems);
            throw e;
        }
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

    private void validateCreateRequest(CreateOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
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

    private void rollbackAdjustedStock(List<CreateOrderItemRequest> deductedItems) {
        for (CreateOrderItemRequest item : deductedItems) {
            try {
                adjustProductStock(item.getProductId(), item.getQuantity());
            } catch (RuntimeException ignored) {
                // Best effort rollback because current stage does not introduce distributed transactions.
            }
        }
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
