CREATE TABLE IF NOT EXISTS products (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    category_id BIGINT DEFAULT NULL COMMENT '分类ID',
    product_name VARCHAR(128) NOT NULL COMMENT '商品名称',
    price DECIMAL(10, 2) NOT NULL COMMENT '商品价格',
    stock INT NOT NULL DEFAULT 0 COMMENT '库存',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1上架 0下架',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_products_name (product_name),
    -- category_id 等值查询（商品分类列表页）；不建 status 单列索引：
    -- 仅 2 个取值的低基数索引几乎不会被优化器选中，且现有查询没有 status 条件
    KEY idx_products_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';
