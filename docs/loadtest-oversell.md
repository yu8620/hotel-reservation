# 库存并发压测（防超卖）

> 状态：已落地（2026-09-13）  
> 目的：证明日历库存在 Redis Lua + MySQL 条件更新下，高并发抢同一晚 **不会超卖**。

## 场景

| 项 | 值 |
|----|----|
| 房型 | 南昌万达嘉华 · 豪华大床房 |
| 
oom_type_id | 1 |
| 库存 | **3** 间 / 晚 |
| 每单 | 1 间 |
| 接口 | POST /api/orders（JWT：demo/demo123） |
| 前置 | 重置该晚 MySQL vailable，再 POST /api/admin/inventory/reload/{id} |

每个请求使用不同 
equestId，避免幂等合并干扰计数。

## 结果

| 时间 | 并发 | 库存 | 成功单 | 售罄（业务码） | MySQL available | Redis | 超卖 | 墙钟 |
|------|------|------|--------|----------------|-----------------|-------|------|------|
| 2026-09-13 | 50 | 3 | **3** | 47 | 0 | 0 | 否 | ~280ms |
| 2026-09-13 | 100 | 3 | **3** | 97 | 0 | 0 | 否 | ~262ms |

判定通过条件：successOrders == stock，且 MySQL / Redis 扣完后均为 0（不为负），oversellDetected == false。

机器可读明细：[loadtest-oversell-latest.json](./loadtest-oversell-latest.json)。

## 复现

`powershell
# 需本地 http://localhost:8080 已启动，MySQL + Redis 可用
powershell -ExecutionPolicy Bypass -File .\scripts\loadtest-oversell.ps1 -Concurrency 100 -Stock 3 -RoomTypeId 1
`

脚本会：登录 admin/demo → 重置指定晚库存 → reload Redis → 线程池并发下单 → 写 docs/loadtest-oversell-*.json。

## 和实现的对应关系

1. **Redis Lua**（lua/deduct_inventory.lua）：先检查入住区间每一晚，再统一 DECRBY，避免跨日半扣。
2. **MySQL**（deductIfEnough）：vailable >= n 条件更新，Redis 异常时仍挡住超订。
3. **失败回补**：落库失败走 
estoreRedis；未支付关单走 MQ TTL+DLX / 定时扫描回补。

## 面试口述（约 20 秒）

「压测房型只有 3 间，我用脚本打 100 个并发下单。成功订单刚好 3 单，其余返回售罄，MySQL 和 Redis 库存都归零，没有超卖。扣减路径是 Lua 原子 check-and-deduct，数据库再闸一道。」

## 尚未覆盖（可选下一档）

- 跨多晚（例如连住 2 晚）区间扣减压测
- 人为停 Redis，只走 MySQL 降级时的并发数字
- 报告 QPS/延迟分位（当前只证明正确性，不吹吞吐）
