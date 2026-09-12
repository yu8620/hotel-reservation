# 栖居酒店预订系统

面向实习面试的后端项目：不是换皮电商，核心是 **「房型 × 日期」日历库存**。预订 8/20 入住、8/23 离店，占用的是 20/21/22 三晚，而不是 SKU 上的一个整数。

技术栈：Spring Boot 4.1 + Java 21 + MyBatis-Plus + MySQL + Redis + Elasticsearch + RabbitMQ。

## 启动

需要本机 **JDK 21** 和 Docker。没有 Docker 时，自行安装 MySQL 8、Redis 7、RabbitMQ，Elasticsearch 可选（挂了会降级 MySQL 搜索）。

本机 MySQL root 密码若不是 `123456`，改 `src/main/resources/application.yaml` 里的 `spring.datasource.password`。3306 已被占用时不要再起 compose 里的 MySQL，只用 redis / rabbitmq / elasticsearch 三个容器。

```bash
docker compose up -d
./mvnw spring-boot:run
```

Windows：`mvnw.cmd spring-boot:run`

打开 http://localhost:8080

| 账号 | 密码 | 角色 |
|------|------|------|
| demo | demo123 | 用户 |
| admin | admin123 | 管理员 |

压测房型：`南昌万达嘉华酒店` 的 **豪华大床房**（`room_type_id = 1`），总共 **3 间**。用 20 个线程抢同一晚，成功订单应恰好 3 笔，库存不为负。

## 面试时先画这张图

```
搜酒店 (ES，挂了降级 MySQL)
    → 用入住离店过滤日历库存 (Redis MGET，miss 回源 MySQL)
        → 下单：Lua 区间原子扣减 → 同一事务写 MySQL 库存+订单
            → 成功：发 TTL 消息到 delay 队列
            → DB 失败：Lua 回补
        → 15 分钟未支付：死信进 close 队列关单并回补
        → 定时任务每 30 秒扫过期订单（MQ 丢消息的兜底）
        → 支付：CAS PENDING_PAY → CONFIRMED（和关单互斥）
```

## 为什么这样设计（被追问时怎么答）

**1. 库存为什么不是一个数字？**  
酒店卖的是某一晚的一间房。跨店连续住会占用多行 `room_inventory(room_type_id, stay_date)`。唯一索引 `(room_type_id, stay_date)`。

**2. Lua 为什么先 GET 全部日期再 DECRBY？**  
如果先扣 20 号再发现 21 号不够，就会出现「扣了一半」的中间态。脚本见 `src/main/resources/lua/deduct_inventory.lua`。

**3. 有了 Lua 为什么 MySQL 还要 `available >= n`？**  
Redis 是加速和跨晚原子性；MySQL 是权威。Redis 误放行或重启后脏数据，数据库这一行条件更新仍然挡住超订。

**4. Lua 成功、写库失败怎么办？**  
catch 里 `restoreRedis`。下单写库用 `TransactionTemplate` 包住「扣 MySQL + 插订单 + 插 order_night」，避免只扣了库存没有订单。

**5. 支付和超时关单同时到？**  
`UPDATE ... WHERE id=? AND status='PENDING_PAY'`，CAS 只有一条成功。

**6. ES 里为什么不存每天房态？**  
房态写得勤，搜到订不上可以接受短暂不准；下单以 Redis/MySQL 为准。ES 负责城市、星级、关键词、地理位置召回。本地 Docker 没有 IK，中文分词弱，所以还有 MySQL `LIKE` 降级。

**7. 为什么用 TTL+DLX 而不是延迟插件？**  
不依赖 `rabbitmq_delayed_message`，面试好画：消息在 delay 队列到期 → 死信到 close 队列。定时扫描是补偿路径。

**8. 取消政策？**  
入住前 24 小时免费取消；之后扣首晚，库存全部回补以便二次销售。

## 关键代码

| 问题 | 文件 |
|------|------|
| 区间 Lua | `src/main/resources/lua/deduct_inventory.lua` |
| 库存网关与崩溃语义 | `inventory/InventoryService.java` |
| 下单 / 支付 / 关单 | `service/OrderService.java` |
| 状态机 | `enums/OrderStatus.java` |
| 搜索 + 降级 | `service/HotelQueryService.java` |
| TTL + DLX | `config/RabbitMqConfig.java` |

## 接口摘要

- `POST /api/auth/register|login`
- `GET /api/hotels/search?city=南昌&checkIn=&checkOut=&keyword=`
- `GET /api/hotels/{id}`
- `POST /api/orders`  body: `{roomTypeId, checkIn, checkOut, rooms, requestId}`
- `POST /api/orders/{id}/pay`  模拟支付
- `POST /api/orders/{id}/cancel`
- `GET /api/orders/mine`
- `POST /api/admin/es/rebuild`  需 admin

`requestId` 有唯一索引，重复提交返回同一订单。

## 简历可写的四条（有压测数据后再填数字）

1. 设计「房型 + 日期」日历库存，入住区间通过 Redis + Lua 原子扣减，避免并发超订和跨日部分成功。  
2. Elasticsearch 做酒店关键词 / 城市 / 星级 / 地理位置召回，房态与 ES 解耦，先召回再校验日历。  
3. RabbitMQ TTL + 死信处理未支付关单并回补房态；支付回调按订单状态 CAS 幂等。  
4. Redis 宕机时降级 MySQL 条件更新；MQ 丢失时用定时任务补偿关单。

不要写没跑过的 QPS。压测建议用 JMeter 打 `POST /api/orders`，同一 `roomTypeId=1`、同一入住日期、不同 `requestId`。

## 设计文档

- 比价读路径 Redis 缓存：[docs/cache-design.md](docs/cache-design.md)
