-- 博学优课初始化脚本（容器首次启动时自动执行）
SET NAMES utf8mb4;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

USE boxue;

CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(64)  NOT NULL COMMENT '登录名',
    `password`      VARCHAR(128) NOT NULL COMMENT '密码（后续会改成 BCrypt）',
    `nickname`      VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `phone`         VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `course` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`         VARCHAR(128)   NOT NULL COMMENT '课程标题',
    `cover`         VARCHAR(255)   DEFAULT NULL COMMENT '封面图 URL',
    `price`         DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '价格（元）',
    `description`   TEXT           COMMENT '课程简介',
    `teacher_name`  VARCHAR(64)    DEFAULT NULL COMMENT '讲师名',
    `status`        TINYINT        NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
    `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';

CREATE TABLE IF NOT EXISTS `orders` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`      VARCHAR(64)    NOT NULL COMMENT '订单号',
    `user_id`       BIGINT         NOT NULL COMMENT '下单用户',
    `course_id`     BIGINT         NOT NULL COMMENT '购买课程',
    `amount`        DECIMAL(10, 2) NOT NULL COMMENT '实付金额',
    `status`        TINYINT        NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已取消',
    `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `pay_time`      DATETIME       DEFAULT NULL COMMENT '支付时间',
    `cancel_time`   DATETIME       DEFAULT NULL COMMENT '取消时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status_create` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 演示数据改由 Spring 启动时写入，避免 Windows 挂载 SQL 文件编码乱码
