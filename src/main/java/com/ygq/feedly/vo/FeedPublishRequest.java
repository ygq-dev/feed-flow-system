package com.ygq.feedly.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class FeedPublishRequest {

    @NotBlank(message = "内容不能为空")
    @Size(max = 1000, message = "内容不能超过1000字")
    private String content;

    private List<String> images;

    private String videoUrl;

    /**
     * 可见范围：0-公开 1-仅好友 2-仅自己
     */
    private Integer visibleRange = 0;
}