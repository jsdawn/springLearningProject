# Sentinel 控制台配置与启动

本项目各服务已集成 Sentinel 1.8.6（限流 / 熔断降级），规则统一维护在 Nacos 配置中心，Sentinel 控制台仅用于监控与查看。集成细节见 [Sentinel 集成说明](../development/Sentinel集成说明.md)。

## 1. 启动本地控制台

1. 下载控制台 jar（与客户端版本一致）：
   `https://github.com/alibaba/Sentinel/releases/download/1.8.6/sentinel-dashboard-1.8.6.jar`
2. 启动控制台（端口 8858，避开网关 8080）：

   ```bash
   java -Dserver.port=8858 -Dcsp.sentinel.dashboard.server=localhost:8858 -Dproject.name=sentinel-dashboard -jar sentinel-dashboard-1.8.6.jar
   ```

3. 浏览器访问 `http://localhost:8858`，默认账号密码 `sentinel / sentinel`。

> 也可以使用 Docker 与 MySQL / Nacos / Redis 一键统一启动，见 [dev-env-docker](../../dev-env-docker/README.md)。

## 2. 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SENTINEL_DASHBOARD` | `localhost:8858` | Sentinel 控制台地址 |

`.env` 示例：

```env
SENTINEL_DASHBOARD=localhost:8858
```

## 3. 使用说明

- 各服务配置了 `eager: true`，启动后自动注册到控制台，可在左侧菜单查看簇点链路、实时监控、机器列表
- 控制台"流控规则/熔断规则"页展示从 Nacos 加载的规则。**修改规则请到 Nacos 配置中心修改对应 dataId**，保存后实时推送生效
- 直接在控制台新增/修改的规则只推送到客户端内存（重启丢失，且会被 Nacos 配置的下一次推送覆盖）
