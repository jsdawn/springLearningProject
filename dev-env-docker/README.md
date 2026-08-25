# Dev Env (Docker)

用于在本地调试时一键启动 Nacos / Redis / Sentinel，Java 项目仍在宿主机（IDEA）本地运行。

## 初始化

在当前目录复制一份环境变量文件到 `.env`，如需改端口或版本，编辑 `.env`。

默认通过 `COMPOSE_PROJECT_NAME` 作为基础工具环境的名称（容器会以该名称作为前缀），需要自定义的话在 `.env` 里修改。

## 启动/停止

在当前目录运行：

```bash
docker compose up -d
docker compose ps
```

停止：

```bash
docker compose down
```

也可以双击运行：

- `scripts/up.cmd`
- `scripts/down.cmd`
- `scripts/logs.cmd`

## 访问地址

- Nacos: http://localhost:8848/nacos
- Redis: localhost:6379
- Sentinel Dashboard: http://localhost:8858

## 本地 Java 项目连接配置

当 Java 项目在宿主机运行时，统一使用 `localhost` 访问：

- Nacos: `localhost:8848`
- Redis: `localhost:6379`
- Sentinel dashboard: `localhost:8858`

## 常用调试命令

以下命令都在本目录（`dev-env-docker/`）执行。

### 1) 查看状态/端口

```bash
docker compose ps
docker compose port nacos 8848
docker compose port redis 6379
docker compose port sentinel 8858
```

### 2) 查看日志

```bash
docker compose logs -f
docker compose logs -f nacos
docker compose logs -f redis
docker compose logs -f sentinel
```

只看最近 200 行：

```bash
docker compose logs --tail=200 nacos
```

### 3) 进入容器（终端控制台）

进入容器 shell：

```bash
docker compose exec nacos sh
docker compose exec redis sh
docker compose exec sentinel sh
```

如果容器没有 `sh`，可以尝试 `bash`：

```bash
docker compose exec nacos bash
```

### 4) Redis 常用调试命令

进入 redis-cli：

```bash
docker compose exec redis redis-cli
```

在 redis-cli 内：

```bash
PING
INFO
KEYS *
GET some:key
TTL some:key
```

### 5) 重启/重建容器

重启单个服务：

```bash
docker compose restart nacos
docker compose restart redis
docker compose restart sentinel
```

重建（修改了 compose 或镜像版本后）：

```bash
docker compose up -d --force-recreate
```

### 6) 清理（遇到脏数据/状态异常）

停止并删除容器：

```bash
docker compose down
```

停止并删除容器 + 清理匿名卷（谨慎，会清掉容器内持久化数据）：

```bash
docker compose down -v
```

### 7) 排查“端口被占用”

先看 compose 是否已经起过：

```bash
docker compose ps
```

再看占用端口的容器：

```bash
docker ps --format "table {{.Names}}\t{{.Ports}}\t{{.Status}}"
```
