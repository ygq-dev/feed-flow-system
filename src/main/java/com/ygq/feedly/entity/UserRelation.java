package com.ygq.feedly.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_relation")
public class UserRelation {

    private Long id;

    /**
     * 粉丝ID（关注者）
     */
    private Long followerId;

    /**
     * 被关注者ID
     */
    private Long followeeId;

    private LocalDateTime createTime;

    private Integer isDeleted;
}