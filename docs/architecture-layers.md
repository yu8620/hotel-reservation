# 分层与依赖（对齐阿里《Java 开发手册》思路）

> 目标：职责清晰、依赖单向；不引入微服务，不改变现有 HTTP 契约。

## 分层

```text
web（Controller）
    ↓
service（应用服务：Order / HotelQuery / Auth / Admin）
    ↓
  inventory / cache / search / mq   （领域或技术组件）
    ↓
mapper（DAO）
    ↓
MySQL / Redis / ES / RabbitMQ
```

| 层 | 包 | 允许 | 禁止 |
|----|----|------|------|
| 接口层 | `web` | 调 Service、校验、组装 `ApiResult` | 直接注入 Mapper / RedisTemplate / Lua |
| 应用服务 | `service` | 编排业务流程、开事务边界 | 处理 HTTP 细节 |
| 领域/组件 | `inventory` `cache` `search` `mq` | 封装单一技术能力 | 依赖 `web` |
| DAO | `mapper` + `entity` | SQL / MP 访问 | 业务判断（少量条件更新语句除外） |
| 模型 | `dto` `enums` `common` | 跨层传输与错误码 | 夹带基础设施客户端 |

## 关键解耦点

1. **读缓存 vs 库存扣减**：`cache` 只服务比价/详情；下单只走 `inventory`（Lua + MySQL）。
2. **Controller 不越层**：管理端运维接口统一经 `AdminService`（不再直接 `RoomTypeMapper`）。
3. **失败策略分路径**：读可降级 MySQL；扣减 Redis 不可用则失败关闭（见 `InventoryService`）。

## 与手册的对应关系

手册强调「应用分层、上层依赖下层、避免跨层」。本项目体量下用 **Service + 专项组件** 代替再拆一套 Manager，避免过度设计；若某块逻辑被多个 Service 复用且变厚，再抽 Manager。

## 相关文档

- 缓存设计：[cache-design.md](./cache-design.md)
- 防超卖压测：[loadtest-oversell.md](./loadtest-oversell.md)
