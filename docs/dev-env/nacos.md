# Nacos 配置与启动

当前 `gateway-service`、`user-service`、`product-service`、`order-service` 均已集成 Nacos（2.5.3以下版本）注册中心，用于服务发现。

## 1. 启动本地 Nacos

从 [Nacos GitHub Releases](https://github.com/alibaba/nacos/releases) 下载 Nacos Server 压缩包，解压后执行：

```bash
# Windows 单机模式启动
startup.cmd -m standalone
```

启动成功后访问：`http://localhost:8848/nacos`（默认账号密码：nacos/nacos）

> 也可以使用 Docker 与 MySQL / Redis / Sentinel 一键统一启动，见 [dev-env-docker](../../dev-env-docker/README.md)。

## 2. Nacos 环境变量

三个 Nacos 相关变量支持通过 `.env` 配置：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `NACOS_SERVER_ADDR` | `localhost:8848` | Nacos 服务地址 |
| `NACOS_NAMESPACE` | （空） | 命名空间 ID，留空使用 public |
| `NACOS_GROUP` | `DEFAULT_GROUP` | 服务分组名称 |

## 3. `.env` 文件补充示例

如果你使用本地 Nacos 默认配置，无需额外配置。如需自定义，在 `.env` 中添加：

```env
NACOS_SERVER_ADDR=localhost:8848
NACOS_NAMESPACE=
NACOS_GROUP=DEFAULT_GROUP
```
