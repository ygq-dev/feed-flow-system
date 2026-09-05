package com.ygq.feedly.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feed_detail")
public class FeedDetail {

    /**
     * 动态ID（雪花算法生成）
     */
    private Long id;

    /**
     * 发布者用户ID
     */
    private Long userId;

    /**
     * 文字内容
     */
    private String content;

    /**
     * 图片URL列表，JSON数组格式：["url1","url2"]
     */
    private String images;

    /**
     * 视频URL
     */
    private String videoUrl;

    /**
     * 可见范围：0-公开 1-仅好友 2-仅自己
     */
    private Integer visibleRange;

    /**
     * 点赞数（冗余）
     */
    private Integer likeCount;

    /**
     * 评论数（冗余）
     */
    private Integer commentCount;

    /**
     * 转发数（冗余）
     */
    private Integer forwardCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer isDeleted;
}