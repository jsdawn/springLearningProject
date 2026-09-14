INSERT INTO users (id, username, password, nickname, phone, email, user_type, status)
VALUES (1, 'admin', '$2a$10$e0MYzXyjpJS2xKz5v6nYEOJz0YzUKGwEuKXXSb9VjA36TObgGJE0a', '系统管理员', '13800000001', 'admin@example.com', 1, 1);

INSERT INTO users (id, username, password, nickname, phone, email, user_type, status)
VALUES (2, 'alice', '$2a$10$e0MYzXyjpJS2xKz5v6nYEOJz0YzUKGwEuKXXSb9VjA36TObgGJE0a', '普通用户Alice', '13800000002', 'alice@example.com', 2, 1);

-- ============================================================
-- RBAC 角色权限种子数据（对应 schema.sql 的 sys_* 四张表）
-- 依赖四张表的唯一键（uk_sys_role_code / uk_sys_permission_code /
-- uk_sys_user_role / uk_sys_role_permission），INSERT IGNORE 幂等，
-- 重复执行不产生重复绑定
-- ============================================================

-- 角色：管理员 / 普通用户（USER 角色不绑权限点：查询与下单链路不设权限门槛，登录即可）
INSERT IGNORE INTO sys_role (id, role_code, role_name, remark) VALUES
(1, 'ADMIN', '管理员', '拥有全部功能权限'),
(2, 'USER', '普通用户', '仅用户侧行为，无管理权限点');

-- 权限点：与 Controller 上 @RequirePermission 注解一一对应
INSERT IGNORE INTO sys_permission (id, perm_code, perm_name) VALUES
(1,  'users:list',    '用户列表查询'),
(2,  'users:page',    '用户分页查询'),
(3,  'users:detail',  '用户详情查询'),
(4,  'users:create',  '创建用户'),
(5,  'users:update',  '更新用户'),
(6,  'users:status',  '启用/禁用用户'),
(7,  'products:create', '创建商品'),
(8,  'products:update', '更新商品'),
(9,  'products:status', '上架/下架商品'),
(10, 'products:stock',  '管理台调整库存');

-- ADMIN 角色 → 全部权限点
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE status = 1;

-- 用户绑定角色：admin → ADMIN、alice → USER（按 username 匹配自增 ID，兼容不同环境）
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT id, 1 FROM users WHERE username = 'admin';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT id, 2 FROM users WHERE username = 'alice';
