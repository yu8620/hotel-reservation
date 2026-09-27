-- 社交：关注 / 探店笔记 / 点赞 / 评论 / 评分（参考黑马点评能力面）

ALTER TABLE `sys_user`
    ADD COLUMN `nickname` VARCHAR(64) DEFAULT NULL AFTER `phone`,
    ADD COLUMN `avatar` VARCHAR(255) DEFAULT NULL AFTER `nickname`,
    ADD COLUMN `bio` VARCHAR(255) DEFAULT NULL AFTER `avatar`,
    ADD COLUMN `is_blogger` TINYINT NOT NULL DEFAULT 0 AFTER `bio`;

CREATE TABLE IF NOT EXISTS `user_follow` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT,
    `follower_id`  BIGINT   NOT NULL COMMENT '关注者',
    `followee_id`  BIGINT   NOT NULL COMMENT '被关注者（好友/博主）',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_follower_followee` (`follower_id`, `followee_id`),
    KEY `idx_followee` (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `blog_note` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `author_id`      BIGINT        NOT NULL,
    `hotel_id`       BIGINT        NOT NULL,
    `title`          VARCHAR(128)  NOT NULL,
    `content`        TEXT          NOT NULL,
    `cover_url`      VARCHAR(255)  DEFAULT NULL,
    `author_score`   TINYINT       DEFAULT NULL COMMENT '作者探店评分 1-5',
    `like_count`     INT           NOT NULL DEFAULT 0,
    `comment_count`  INT           NOT NULL DEFAULT 0,
    `rating_sum`     INT           NOT NULL DEFAULT 0 COMMENT '读者评分累加',
    `rating_count`   INT           NOT NULL DEFAULT 0,
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_hotel_created` (`hotel_id`, `created_at`),
    KEY `idx_author_created` (`author_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `note_like` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `note_id`    BIGINT   NOT NULL,
    `user_id`    BIGINT   NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_user` (`note_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `note_comment` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `note_id`    BIGINT       NOT NULL,
    `user_id`    BIGINT       NOT NULL,
    `content`    VARCHAR(512) NOT NULL,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_note_created` (`note_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `note_rating` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `note_id`    BIGINT   NOT NULL,
    `user_id`    BIGINT   NOT NULL,
    `score`      TINYINT  NOT NULL COMMENT '1-5',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_user_rating` (`note_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
