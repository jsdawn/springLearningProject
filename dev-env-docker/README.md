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
