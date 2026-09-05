package com.ygq.feedly.controller;

import com.ygq.feedly.common.Result;
import com.ygq.feedly.service.LikeService;
import com.ygq.feedly.vo.LikeStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/like")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    /**
     * 点赞
     */
    @PostMapping("/{feedId}")
    public Result<String> like(@PathVariable Long feedId,
                               @RequestParam(required = false) Long userId) {
        if (userId == null) {
            userId = 1L;
        }
        likeService.like(feedId, userId);
        return Result.success("点赞成功");
    }

    /**
     * 取消点赞
     */
    @DeleteMapping("/{feedId}")
    public Result<String> unlike(@PathVariable Long feedId,
                                 @RequestParam(required = false) Long userId) {
        if (userId == null) {
            userId = 1L;
        }
        likeService.unlike(feedId, userId);
        return Result.success("取消点赞成功");
    }

    /**
     * 查询点赞状态
     */
    @GetMapping("/status/{feedId}")
    public Result<LikeStatusVO> status(@PathVariable Long feedId,
                                       @RequestParam(required = false) Long userId) {
        if (userId == null) {
            userId = 1L;
        }
        boolean liked = likeService.isLiked(feedId, userId);
        long count = likeService.getLikeCount(feedId);
        return Result.success(new LikeStatusVO(liked, count));
    }
}