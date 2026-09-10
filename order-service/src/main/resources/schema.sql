CREATE TABLE IF NOT EXISTS orders (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单编号',
    user_id BIGINT NOT NULL COMMENT '下单用户ID',
    total_amount DECIMAL(10, 2) NOT NULL COMMENT '订单总金额',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1已创建(待支付) 2已取消 3超时关闭',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    -- (user_id, status) 联合索引：覆盖"按用户查订单"和"按用户+状态查订单"两类查询。
    -- 左前缀 user_id 已覆盖单独按用户查的场景，故不再保留单列 idx_orders_user_id；
    -- status 只有 3 个取值，低基数单列索引几乎不会被选中，不单独建
    KEY idx_orders_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单明细ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    product_name VARCHAR(128) NOT NULL COMMENT '商品名称快照',
    product_price DECIMAL(10, 2) NOT NULL COMMENT '商品单价快照',
    quantity INT NOT NULL COMMENT '购买数量',
    amount DECIMAL(10, 2) NOT NULL COMMENT '明细金额',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_order_item_order_id (order_id),
    KEY idx_order_item_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';
