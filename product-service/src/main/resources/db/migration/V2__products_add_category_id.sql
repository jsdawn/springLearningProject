-- V2: products 新增分类列 + 分类索引
-- 背景：P3 数据库专项。商品表此前无分类维度，补充 category_id 列
--       以支撑"按分类查商品"的高频查询路径。
-- 说明：- 同步删除低基数的 status 单列索引（仅 1/0 两个取值）；
--         如未来出现"按 status 筛选商品"的查询，届时建 (status, ...) 组合而非单列
--       - 存量商品按 id 补 1~10 轮转分类（测试数据整理，可按业务需要改）
--       - 新库由 schema.sql 直接建出含新列的表；本脚本供"已存在的库"手动执行对齐，只跑一次。
ALTER TABLE products
    ADD COLUMN category_id BIGINT DEFAULT NULL COMMENT '分类ID' AFTER product_name,
    ADD INDEX idx_products_category (category_id),
    DROP INDEX idx_products_status;

UPDATE products SET category_id = 1 + (id % 10) WHERE category_id IS NULL;
