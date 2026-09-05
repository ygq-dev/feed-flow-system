package com.ygq.feedly.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FeedVO {
    private Long feedId;
    private Long userId;
    private String nickname;
    private String avatar;
    private String content;
    private String images;
    private String videoUrl;
    private Integer likeCount;
    private Integer commentCount;
    private LocalDateTime createTime;
}