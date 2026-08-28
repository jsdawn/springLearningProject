# Redis 配置与启动

当前 `product-service` 已集成 Redis 缓存（商品详情缓存，Key 规范：`product:info:{id}`，过期时间 30 分钟）。Redis 仅作热点缓存，断电丢失不影响业务，持久化以 MySQL 为准。

## 1. 安装 Redis

- Windows：从 [tporadowski/redis/releases](https://github.com/tporadowski/redis/releases) 下载 `.zip` 或 `.msi` 安装包，解压/安装后目录内含 `redis-server.exe` 和 `redis-cli.exe`
- Linux：`sudo apt install redis-server`
- macOS：`brew install redis`

> 也可以使用 Docker 与 MySQL / Nacos / Sentinel 一键统一启动，见 [dev-env-docker](../../dev-env-docker/README.md)。

## 2. 启动 Redis 服务

```bash
# Windows：在解压目录执行（带配置文件启动）
redis-server.exe redis.windows.conf

# Linux / macOS
redis-server
```

## 3. redis-cli 常用命令

```bash
# 连接 Redis（默认 127.0.0.1:6379）
redis-cli
# 监控 Redis（可选，新开终端）
redis-cli monitor
# 连通性测试，成功返回 PONG
ping
# 查看商品缓存
get product:info:1
# 查看剩余过期时间（秒）
ttl product:info:1
# 判断 key 是否存在（1 存在 / 0 不存在）
exists product:info:1
# 删除商品缓存
del product:info:1
# 查看全部 key（仅本地调试使用）
keys *
# 退出
exit
```

## 4. Redis 环境变量

三个 Redis 相关变量支持通过 `.env` 配置（当前无密码）：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `REDIS_HOST` | `localhost` | Redis 服务地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_DB` | `0` | 数据库索引 |
