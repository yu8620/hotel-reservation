# 比价读路径 Redis 缓存设计

> 状态：P0–P4 已落地；已补防穿透（空对象）、防雪崩（TTL 抖动）  
> 范围：只优化「搜酒店 / 看详情 / 比价」读多写少路径  
> 不动：下单 Lua 日历库存、支付、超时关单  
> 对齐接口：`GET /api/hotels/search`、`GET /api/hotels/{id}`  
> 对齐代码：`HotelQueryService`、`InventoryService`（回源算价/日历时）

---

## 0. 目标与边界

| 项 | 约定 |
|----|------|
| 目标 | 比价时列表 / 详情 / 报价尽量打 Redis，降低 ES、MySQL 压力，加快翻页与来回点开 |
| 允许脏读 | 列表「有房 / 起价」可延迟 ≤ TTL；**下单仍以 Lua + MySQL 为准** |
| 读缓存禁止事项 | 不得用读缓存结果直接扣库存或跳过 Lua |
| 策略 | Cache Aside；key 必须带齐日期等查询条件；搜索列表靠短 TTL，报价在写库存后精确失效 |

### 读 vs 写职责拆分

| 阶段 | Redis 角色 | 能否短暂不准 |
|------|------------|----------------|
| 搜列表 / 详情 / 比价 | 读缓存、报价摘要 | 可以（≤ TTL） |
| 下单 | Lua 原子扣日历库存 | **必须准** |

---

## 1. Key 规范

统一前缀：`hr:`（hotel-reservation）。版本号写在 key 里，不兼容变更时递增 `v1` → `v2`。

### L1 搜索列表

```text
hr:search:v1:{city}:{checkIn}:{checkOut}:{rooms}:{star|n}:{keyword|n}:{minP|n}:{maxP|n}:{page}:{size}
```

| 项 | 说明 |
|----|------|
| Value | `PageResponse<HotelCardResponse>` JSON |
| TTL | **45s** |
| 说明 | 同一筛选连刷只回源一次 |
| 地理搜索 | 带 `latitude/longitude/radiusKm` 的请求默认**先不缓存**（key 爆炸）；可选后续：`hr:searchgeo:v1:{geohash5}:{checkIn}:{checkOut}:{rooms}:{page}:{size}` |

### L2 酒店静态详情（与日期无关）

```text
hr:hotel:static:v1:{hotelId}
```

| 项 | 说明 |
|----|------|
| Value | 名称、地址、星级、设施、描述、房型基础信息（**不含** `minRemain` / `calendar`） |
| TTL | **30min** |
| 失效 | 管理端改酒店资料时删除 |

### L3 区间报价摘要（比价最关键）

```text
hr:quote:v1:{hotelId}:{checkIn}:{checkOut}:{rooms}
```

| 项 | 说明 |
|----|------|
| Value 示例 | `{ "minStayPrice", "minRemain", "roomTypes":[{"id","name","minRemain","stayPrice"}] }` |
| TTL | **30s** |
| 用途 | 填充 `HotelCardResponse` 的 `minStayPrice` / `minRemain` / `availableRoomTypes`；列表页对候选酒店 **MGET** 批量补齐 |

### L4 房型日历片段（详情页）

```text
hr:cal:v1:{roomTypeId}:{checkIn}:{checkOut}
```

| 项 | 说明 |
|----|------|
| Value | 区间内每天 `price` / `available`（对应 `NightPrice`） |
| TTL | **20s** |
| 回源 | `InventoryService.loadRows`（可顺带 `warmUp`） |
| 注意 | **不能**替代下单扣减 |

### L5 可选：用户对比栏 / 浏览历史

```text
hr:compare:v1:{userId|anonSid}
```

| 项 | 说明 |
|----|------|
| Value | 最近浏览 `hotelId[]`（最多 10～20） |
| TTL | **24h**（建议滑动过期） |

---

## 2. 读路径改造（按接口）

### 2.1 `HotelQueryService.search(HotelSearchQuery)`

对应：`GET /api/hotels/search`

**步骤**

1. 规范化 `checkIn` / `checkOut` / `rooms` / `page` / `size`（与现逻辑一致）
2. 计算 L1 key → `GET`
3. 命中 → 直接返回；可将 `HotelCardResponse.searchSource` 标为 `CACHE`（字段已存在）
4. 未命中 → 现有 ES / MySQL 召回得到候选 `hotelId[]`
5. 对候选 id **批量** `MGET` L3；缺失的回源算价后 `SETEX` L3
6. 组装 `PageResponse<HotelCardResponse>` → `SETEX` L1 → 返回

**验收**

- [ ] 同一条件连续 search 两次，第二次命中缓存（或 `searchSource=CACHE`）
- [ ] 修改 `rooms` 或日期后不串数据
- [ ] Redis 不可用时降级直打现有逻辑，接口仍可用

### 2.2 `HotelQueryService.detail(hotelId, checkIn, checkOut)`

对应：`GET /api/hotels/{id}`

**步骤**

1. `GET` L2 static；未命中则查库写 L2
2. 若有入住日期：对各房型 `GET` L4（或先用 L3 拼摘要）；未命中则 `loadRows` 写 L4
3. 内存组装 `HotelDetailResponse` = 静态 ∪ 日历 / 剩余
4. **不要**把「带日期的整包 detail」做成超长 TTL 单 key

**验收**

- [ ] 反复打开同一酒店，静态信息走 L2
- [ ] 仅改日期时主要失效 / 重算 L3、L4，而不是整表重查酒店主数据

---

## 3. 失效与更新

| 事件 | 操作 |
|------|------|
| 下单扣减成功 / 支付成功 / 取消回补 / 超时关单回补 | 删该 `hotelId` 相关 L3；删涉及 `roomTypeId` 的 L4（尽量带日期精确删） |
| 管理端改酒店静态信息 | 删 L2；L1 靠短 TTL 自然过期（或 key 版本号懒失效） |
| `rebuildIndex` / `reloadRoomType` | 删对应 L4 + 所属酒店 L3 |
| 读缓存未命中 | 回源后写入；不在此路径删别人的 key |

**推荐策略**

- L1：主要依赖短 TTL，下单时不扫全库 search key
- L3 / L4：在 `OrderService` 写库存成功路径上精确 `DEL` / 按前缀删

---

## 4. 代码落点（待实现时勾选）

| 项 | 说明 | 状态 |
|----|------|------|
| `cache/HotelCacheKeys.java` | 集中拼 key | 已完成 |
| `cache/HotelReadCache.java` | get/put/mget/evict 封装 | 已完成 |
| `HotelQueryService` | search / detail 包读缓存 | 已完成 |
| `OrderService` | 扣减 / 回补后 `evictAfterInventoryChange` | 已完成 |
| `hotel.cache.*` 配置 | 开关、各层 TTL | 已完成 |
| 可选 Caffeine | 仅 L2 本地二级缓存 1～5min | 可选 |
| 可选 L5 对比栏 | 前端对比 / 浏览历史 | 可选 |

序列化：Jackson JSON。单 key 体积控制在一页列表量级。

---

## 5. 配置草案（实现时写入 `application.yaml`）

```yaml
hotel:
  cache:
    enabled: true
    search-ttl-seconds: 45
    static-ttl-seconds: 1800
    quote-ttl-seconds: 30
    calendar-ttl-seconds: 20
    compare-ttl-seconds: 86400
    null-object-ttl-seconds: 120   # 详情不存在时的空对象 TTL
    ttl-jitter-seconds: 15         # 实际 TTL = 基础值 + random[0, jitter]，防雪崩
```

---

## 6. 观察与压测

**指标（可后补 Micrometer）**

- `cache_hit{layer=search|static|quote|cal}`
- `cache_miss`、`cache_evict`
- 搜索 / 详情 P99（有无缓存对比）

**功能用例**

- [ ] 同条件 search 两次，二次命中
- [ ] 不同 `rooms` 不互相命中
- [ ] detail 无日期 vs 有日期
- [ ] 下单后 L3 失效，再次 search 剩余 / 价格更新
- [ ] Redis 宕机时读路径降级，下单链路仍按现有 Redis→MySQL 降级

**体验用例**

- [ ] 连续打开 10 家详情，酒店主表查询次数下降
- [ ] 列表来回翻页，ES 查询次数下降

---

## 7. 实施顺序

| 阶段 | 内容 | 状态 |
|------|------|------|
| P0 | Key 工具类 + L2 static 接入 detail | 已完成 |
| P1 | L3 quote 批量拼 search 卡片 | 已完成 |
| P2 | L1 search 整页缓存 | 已完成 |
| P3 | 下单 / 回补失效 L3、L4 | 已完成 |
| P4 | L4 日历缓存 + 读失败降级日志 | 已完成 |
| P5 | 可选：L5 对比栏、Caffeine | 待定 |

每完成一阶段：勾选本节状态，并在下方「变更记录」补一行。

---

## 8. 明确不做

- 用读缓存直接下单或跳过 Lua
- 把每天房态长期写入 ES 文档
- 无 TTL 的永久 search key
- 一上来上多级缓存中台 / 微服务拆分

---

## 9. 简历表述（落地并压测后填数）

> 针对订酒店读多写少的比价场景，将搜索列表、酒店静态详情与短 TTL 区间报价分层缓存到 Redis；列表用 MGET 批量补报价，下单仍走 Lua 日历库存。比价路径缓存命中率 __%，搜索 P99 从 __ms 降到 __ms。

---

## 10. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-09-12 | 初稿：分层 key、读写路径、失效策略、实施顺序 |
| 2026-09-12 | 代码落地 P0–P4：cache 分层（Keys / Snapshot / ReadCache）、Query 组装、Order 失效；读写与库存扣减解耦 |
| 2026-09-13 | 防穿透：详情 L2 缓存空对象（missing + 短 TTL），未上布隆 |
| 2026-09-13 | 防雪崩：write 路径 TTL + random jitter（ttl-jitter-seconds） |

## 11. 防穿透：缓存空对象

> 状态：已落地（详情 L2）；布隆过滤器仍不做

对「缓存和库都不存在」的请求，第一次回源后写入**短 TTL 空标记**，后续直接命中缓存，避免反复打 MySQL。

| 场景 | 做法 | TTL |
|------|------|-----|
| 详情：酒店 id 不存在 | `hr:hotel:static:v1:{id}` 存 `{ "id":..., "missing": true }` | `hotel.cache.null-object-ttl-seconds`（默认 **120s**） |
| 搜索无结果 | 空 `PageResponse` 仍走 L1（45s） | 与 L1 相同 |
| 报价无可用房 | 仍写 L3（`availableRoomTypes=[]`） | 30s |

命中 `missing=true` 时直接返回 404，**不再查库**。真实酒店写入时 `missing=false`，TTL 仍为 30min。

不在此阶段上布隆过滤器（酒店量小，空对象足够讲清穿透）。

| 2026-09-13 | 防穿透：详情 L2 缓存空对象（missing + 短 TTL），未上布隆 |

## 12. 防雪崩：TTL 随机抖动

> 状态：已落地

写入缓存时实际过期时间：

```text
TTL = baseTtlSeconds + random(0, ttl-jitter-seconds)   // 含两端
```

配置：`hotel.cache.ttl-jitter-seconds`（默认 **15**）。

这样同一批搜索/报价 key 不会在同一秒集体失效，打穿 DB/ES 的风险更低。与「空对象防穿透」「写后删防击穿热点」互补。

| 2026-09-13 | 防雪崩：write 路径 TTL + random jitter（ttl-jitter-seconds） |

## 13. 缓存击穿：互斥重建

> 状态：已落地

热点 key 过期瞬间，大量并发同时 miss 回源会把 DB/ES 打穿。这里用 **Redis 互斥锁** 保证同一时刻只有一个请求重建：

`	ext
miss → SET lock NX EX lock-ttl
  ├─ 拿到锁：double-check 缓存 → loader → put → unlock
  └─ 未拿到：sleep(lock-wait-millis) × lock-wait-retries 再读缓存
       └─ 仍 miss：降级自己重建（避免挂死请求）
`

实现：CacheMutex.loadThrough；锁 key：HotelCacheKeys.lockSearch / lockStatic / lockCalendar。

接入点：

| 场景 | 锁 key | 说明 |
|------|--------|------|
| L1 搜索 miss | hr:lock:search:... | 非 geo 搜索 |
| L2 静态 miss | hr:lock:static:{hotelId} | 含 missing 空对象写入 |
| L4 日历 miss | hr:lock:calendar:... | 房型 × 入住离店 |

配置（hotel.cache）：

| 项 | 默认 | 含义 |
|----|------|------|
| lock-ttl-seconds | 5 | 锁持有时间（防死锁） |
| lock-wait-millis | 40 | 等待方每次休眠 |
| lock-wait-retries | 5 | 再读缓存次数 |

解锁用 Lua 比对 token，避免误删别人的锁。订单扣减路径仍走 Lua 库存，**不**走这套读缓存互斥。

| 2026-09-13 | 击穿：Redis SET NX 互斥重建（CacheMutex） |
