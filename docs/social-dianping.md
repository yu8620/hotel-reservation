# 社交探店模块（参考黑马点评）

在日历库存主链路之外，增加轻量社交能力，方便演示「关注 / 探店笔记 / 互动」。

## 能力

| 能力 | 说明 |
|------|------|
| 关注 | `user_follow` 单向关注；双方互相关注即互关好友 |
| 博主 | `sys_user.is_blogger=1`；发笔记自动升为博主 |
| 探店笔记 | `blog_note` 绑定酒店，含作者评分 |
| 点赞 | `note_like` 唯一约束防重复；计数回写笔记 |
| 评论 | `note_comment` |
| 读者评分 | `note_rating` 1-5 分，可改分；笔记上维护 sum/count |

## 主要接口

- `POST /api/social/follow` `{followeeId}`
- `DELETE /api/social/follow/{followeeId}`
- `GET /api/social/users/{id}` 资料（含 followedByMe / mutualFollow）
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

## 设计取舍

- 库存 / 下单 / 支付主链路不动，社交表独立，面试仍以日历库存为主故事。
- 点赞计数先落 MySQL（唯一索引幂等）；后续可把 like Set 迁到 Redis，与黑马点评一致。
- 互关用双向存在性判断，不单独建「好友表」，和点评关注模型一致。
