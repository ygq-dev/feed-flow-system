package com.ygq.feedly.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentVO {
    private Long commentId;
    private Long feedId;
    private Long userId;
    private String content;
    private Integer likeCount;
    private LocalDateTime createTime;
}