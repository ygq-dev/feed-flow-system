package com.ygq.feedly.controller;

import com.ygq.feedly.common.Result;
import com.ygq.feedly.service.FeedPublishService;
import com.ygq.feedly.service.FeedTimelineService;
import com.ygq.feedly.vo.FeedPublishRequest;
import com.ygq.feedly.vo.TimelineResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedPublishService feedPublishService;
    private final FeedTimelineService feedTimelineService;

    /**
     * 发布动态
     */
    @PostMapping("/publish")
    public Result<Long> publish(@RequestBody @Valid FeedPublishRequest request,
                                @RequestParam(required = false) Long userId) {
        // 模拟用户ID（后续集成JWT后替换）
        if (userId == null) {
            userId = 1L;
        }
        Long feedId = feedPublishService.publish(userId, request);
        return Result.success(feedId);
    }

    @GetMapping("/timeline")
    public Result<TimelineResult> timeline(
            @RequestParam(required = false) Long lastFeedId,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) Long userId) {
        // 模拟用户ID（后续集成JWT后替换）
        if (userId == null) {
            userId = 1L;
        }
        TimelineResult result = feedTimelineService.getTimeline(userId, lastFeedId, size);
        return Result.success(result);
    }
}