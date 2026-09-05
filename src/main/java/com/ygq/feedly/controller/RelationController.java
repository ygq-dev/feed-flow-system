package com.ygq.feedly.controller;

import com.ygq.feedly.common.Result;
import com.ygq.feedly.service.RelationService;
import com.ygq.feedly.vo.FollowRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/relation")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;

    /**
     * 关注用户
     */
    @PostMapping("/follow")
    public Result<String> follow(@RequestBody @Valid FollowRequest request,
                                 @RequestParam(required = false) Long userId) {
        if (userId == null) {
            userId = 1L;
        }
        relationService.follow(userId, request.getFolloweeId());
        return Result.success("关注成功");
    }

    /**
     * 取关用户
     */
    @DeleteMapping("/follow/{followeeId}")
    public Result<String> unfollow(@PathVariable Long followeeId,
                                   @RequestParam(required = false) Long userId) {
        if (userId == null) {
            userId = 1L;
        }
        relationService.unfollow(userId, followeeId);
        return Result.success("取关成功");
    }
}