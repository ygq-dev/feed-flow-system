-- src/main/resources/sql/schema.sql

CREATE DATABASE IF NOT EXISTS `feedly` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `feedly`;

-- 动态详情表
DROP TABLE IF EXISTS `feed_detail`;
CREATE TABLE `feed_detail` (
                               `id` BIGINT NOT NULL COMMENT '动态ID（雪花算法生成）',
                               `user_id` BIGINT NOT NULL COMMENT '发布者用户ID',
                               `content` TEXT NOT NULL COMMENT '动态文字内容',
                               `images` VARCHAR(1024) DEFAULT NULL COMMENT '图片URL列表，JSON数组格式',
                               `video_url` VARCHAR(512) DEFAULT NULL COMMENT '视频URL',
                               `visible_range` TINYINT NOT NULL DEFAULT 0 COMMENT '可见范围：0-公开 1-仅好友 2-仅自己',
                               `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数（冗余计数）',
                               `comment_count` INT NOT NULL DEFAULT 0 COMMENT '评论数',
                               `forward_count` INT NOT NULL DEFAULT 0 COMMENT '转发数',
                               `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                               `is_deleted` TINYINT NOT NULL DEFAULT 0,
                               PRIMARY KEY (`id`),
                               KEY `idx_user_id_create_time` (`user_id`, `create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动态详情表';

-- 用户关系表
DROP TABLE IF EXISTS `user_relation`;
CREATE TABLE `user_relation` (
                                 `id` BIGINT NOT NULL AUTO_INCREMENT,
                                 `follower_id` BIGINT NOT NULL COMMENT '粉丝ID（关注者）',
                                 `followee_id` BIGINT NOT NULL COMMENT '被关注者ID',
                                 `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 `is_deleted` TINYINT NOT NULL DEFAULT 0,
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_follower_followee` (`follower_id`, `followee_id`),
                                 KEY `idx_followee_id` (`followee_id`) COMMENT '查某人的粉丝列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关系表';

-- 插入测试用户（方便开发调试）
INSERT IGNORE INTO `user_relation` (`follower_id`, `followee_id`) VALUES
(1, 2), (1, 3), (1, 4),
(2, 1), (2, 3),
(3, 1), (3, 2), (3, 4),
(4, 1);