CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `username`    VARCHAR(32)  NOT NULL,
    `password`    VARCHAR(128) NOT NULL,
    `phone`       VARCHAR(20)  DEFAULT NULL,
    `role`        VARCHAR(16)  NOT NULL DEFAULT 'USER',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `hotel` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(128)  NOT NULL,
    `city`        VARCHAR(32)   NOT NULL,
    `address`     VARCHAR(255)  NOT NULL,
    `star_rating` INT           NOT NULL DEFAULT 3,
    `latitude`    DECIMAL(10,6) NOT NULL,
    `longitude`   DECIMAL(10,6) NOT NULL,
    `description` VARCHAR(512)  DEFAULT NULL,
    `amenities`   VARCHAR(255)  DEFAULT NULL,
    `min_price`   DECIMAL(10,2) NOT NULL DEFAULT 0,
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_city_star` (`city`, `star_rating`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `room_type` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `hotel_id`    BIGINT        NOT NULL,
    `name`        VARCHAR(64)   NOT NULL,
    `occupancy`   INT           NOT NULL DEFAULT 2,
    `bed_desc`    VARCHAR(64)   DEFAULT NULL,
    `total_rooms` INT           NOT NULL,
    `base_price`  DECIMAL(10,2) NOT NULL,
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_hotel_id` (`hotel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 日历库存：权威数据。预订 [check_in, check_out) 等于扣这几行。
CREATE TABLE IF NOT EXISTS `room_inventory` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT,
    `room_type_id` BIGINT        NOT NULL,
    `stay_date`    DATE          NOT NULL,
    `total_rooms`  INT           NOT NULL,
    `available`    INT           NOT NULL,
    `price`        DECIMAL(10,2) NOT NULL,
    `version`      INT           NOT NULL DEFAULT 0,
    `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_type_date` (`room_type_id`, `stay_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `booking_order` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `order_no`       VARCHAR(32)   NOT NULL,
    `request_id`     VARCHAR(64)   NOT NULL,
    `user_id`        BIGINT        NOT NULL,
    `hotel_id`       BIGINT        NOT NULL,
    `room_type_id`   BIGINT        NOT NULL,
    `check_in`       DATE          NOT NULL,
    `check_out`      DATE          NOT NULL,
    `nights`         INT           NOT NULL,
    `rooms`          INT           NOT NULL,
    `amount`         DECIMAL(10,2) NOT NULL,
    `penalty_amount` DECIMAL(10,2) NOT NULL DEFAULT 0,
    `status`         VARCHAR(32)   NOT NULL,
    `expire_time`    DATETIME      DEFAULT NULL,
    `pay_time`       DATETIME      DEFAULT NULL,
    `cancel_time`    DATETIME      DEFAULT NULL,
    `cancel_reason`  VARCHAR(64)   DEFAULT NULL,
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_request_id` (`request_id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_status_expire` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `order_night` (
    `id`         BIGINT        NOT NULL AUTO_INCREMENT,
    `order_id`   BIGINT        NOT NULL,
    `stay_date`  DATE          NOT NULL,
    `rooms`      INT           NOT NULL,
    `price`      DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_date` (`order_id`, `stay_date`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
