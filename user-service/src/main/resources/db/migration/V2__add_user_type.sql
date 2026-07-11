ALTER TABLE users
    ADD COLUMN user_type TINYINT NOT NULL DEFAULT 2 COMMENT '用户类型: 1管理员 2普通用户' AFTER email;

ALTER TABLE users
    ADD KEY idx_users_user_type (user_type);
