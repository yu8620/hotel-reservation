# 日历库存与 Redis Lua 扣减

## 问题

酒店不是 SKU 单数字库存，而是「房型 × 日期」日历库存。连住 N 晚要占用 N 行；并发下既不能超卖，也不能出现「只扣了其中一晚」的中间态。

## 方案

1. MySQL 
oom_inventory(room_type_id, stay_date) 为权威；扣减使用 vailable >= n 条件更新。
2. Redis 每晚一个 key（见 InventoryKeys），下单前 warmUp（SETNX 不覆盖进行中的扣减）。
3. deduct_inventory.lua：**先遍历校验每一晚可用，再统一 DECRBY**；任一夜不够返回 0，整段不动。
4. 
estore_inventory.lua：关单/取消/落库失败时按晚 INCRBY 回补。

## 失败语义（面试高频）

| 场景 | 行为 |
|------|------|
| Lua 成功、DB 失败 | 
estoreRedis，Redis 回到扣减前 |
| Redis 不可用 | **失败关闭**，抛 INVENTORY_UNAVAILABLE，不降级 MySQL 继续卖 |
| 读路径 Redis 挂 | 报价/minAvailable 可降级 MySQL |

## 代码入口

- Lua：src/main/resources/lua/deduct_inventory.lua、
estore_inventory.lua
- 网关：inventory/InventoryService.java
- 下单编排：service/OrderService.java#create

## 验证

见 [loadtest-oversell.md](loadtest-oversell.md)：100 并发抢 3 间，成功 3、零超卖。
