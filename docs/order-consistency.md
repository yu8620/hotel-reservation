# 下单 / 支付 / 超时关单一致性

## 主路径

1. 
equestId 唯一：重复提交返回已有订单。
2. Redis Lua 预扣 → 事务内 MySQL 扣减 + 写订单（PENDING_PAY）+ order_night。
3. 发 RabbitMQ TTL 延迟关单消息；失败不阻断下单（定时任务兜底）。
4. 下单成功后精确失效比价读缓存（报价/日历）。

## 支付

- 只做状态机 CAS：PENDING_PAY → CONFIRMED，**不再扣库存**（创建已 hold）。
- 与关单并发时 CAS 互斥，已确认则幂等返回。

## 超时关单

- 主路径：delay 队列消息 TTL 到期 → DLX → close 队列 → closeIfUnpaid。
- 补偿：OrderTimeoutScheduler 扫描 expireTime 已过且仍待支付的订单。
- 已支付订单：延迟消息仍可能到达，但状态非 PENDING_PAY 时**空转**，不关单、不回补。

## 关单 / 取消回补

closeOrCancel：CAS 改状态成功后才 
estoreMysql + 
estoreRedis + 缓存失效，避免重复回补导致库存虚高。

## 拓扑

见 config/RabbitMqConfig.java：业务交换机 + delay 队列（死信到 close）+ close 队列。
