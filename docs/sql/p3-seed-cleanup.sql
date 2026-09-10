-- ============================================================
-- P3 造数清理脚本：只删除带 seed 前缀/标记的数据，不碰业务数据
-- ============================================================
USE mall_db;

DELETE FROM orders WHERE order_no LIKE 'ORDSEED%';
DELETE FROM products WHERE remark = 'seed' AND product_name LIKE '测试商品%';
