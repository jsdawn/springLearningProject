ALTER TABLE users
    ADD COLUMN password VARCHAR(100) DEFAULT NULL COMMENT '登录密码（BCrypt）' AFTER username;

