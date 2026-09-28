# 社交探店模块（参考黑马点评）

在日历库存主链路之外，增加轻量社交能力，方便演示「关注 / 探店笔记 / 互动」。**面试主故事仍是日历库存与防超卖**；本模块是加分项，不要抢主线。

## 能力一览

| 能力 | 说明 |
|------|------|
| 关注 | `user_follow` 单向关注；双方互相关注即互关好友 |
| 博主 | `sys_user.is_blogger=1`；发笔记自动升为博主 |
| 探店笔记 | `blog_note` 绑定酒店，含作者评分 |
| 点赞 | `note_like` 唯一约束防重复；计数回写笔记 |
| 评论 | `note_comment` |
| 读者评分 | `note_rating` 1-5 分，可改分；笔记上维护 `rating_sum` / `rating_count` |

## 数据模型（Flyway `V2__social.sql`）

```text
sys_user (+ nickname / avatar / bio / is_blogger)
    │
    ├── user_follow (follower_id → followee_id)  UNIQUE(follower, followee)
    │
    └── blog_note (author_id, hotel_id, title, content, like/comment/rating 计数)
            ├── note_like    UNIQUE(note_id, user_id)
            ├── note_comment
            └── note_rating  UNIQUE(note_id, user_id)
```

启动时 Flyway 自动迁移；已有库升级一次即可。种子数据在 `DataSeeder`：博主账号、样例笔记、demo 关注关系。

## 关键代码

| 职责 | 路径 |
|------|------|
| 表结构 | `src/main/resources/db/migration/V2__social.sql` |
| 关注 / 互关 | `service/FollowService.java`、`web/FollowController.java` |
| 笔记 / 赞 / 评 / 分 | `service/BlogNoteService.java`、`web/NoteController.java` |
| 实体 / Mapper | `entity/UserFollow|BlogNote|NoteLike|NoteComment|NoteRating` + 对应 mapper |
| 前端演示 | `src/main/resources/static/index.html`（「探店笔记」面板） |

## 主要接口

前缀：`/api/social/*`（关注与资料）、`/api/notes/*`（笔记与互动）。

- `POST /api/social/follow` `{followeeId}`
- `DELETE /api/social/follow/{followeeId}`
- `GET /api/social/users/{id}` 资料（含 `followedByMe` / `mutualFollow`）
- `GET /api/social/users/{id}/following|followers|mutual`
- `GET /api/social/bloggers`
- `GET /api/notes/feed`、`/hotel/{id}`、`/author/{id}`、`/{id}`
- `POST /api/notes` 发笔记（需登录）
- `POST|DELETE /api/notes/{id}/like`
- `POST /api/notes/{id}/comments`、`GET .../comments`
- `POST /api/notes/{id}/rate` `{score}`

公开读：笔记列表/详情、博主列表、用户公开资料。写操作需 JWT。

## 种子账号

| 账号 | 密码 | 说明 |
|------|------|------|
| traveler | demo123 | 旅居博主「旅居阿宁」 |
| foodie | demo123 | 旅居博主「赣味小满」 |
| demo | demo123 | 已关注两位博主，并与 traveler 互关 |

## 设计取舍（面试怎么说）

1. **边界**：库存 / 下单 / 支付主链路不动，社交表独立；被问到先画日历库存，再一句带过探店。
2. **互关**：双向 `user_follow` 存在性判断，不单独建好友表，和点评「关注模型」一致。
3. **点赞幂等**：MySQL 唯一索引 `(note_id, user_id)` + 计数回写；后续可把 like Set 迁 Redis（黑马点评路径），当前体量先落库更直观。
4. **评分**：读者分与作者分分开；读者侧用 sum/count 便于平均分，避免每次扫表。
5. **鉴权**：读多写少——列表/详情可匿名，关注与发笔记等写操作走 JWT。

## 和主链路的关系

```text
酒店搜索 / 详情 / 下单（主故事）
        │
        └── 同一酒店可挂 blog_note（探店内容，不影响库存扣减）
```
