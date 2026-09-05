package com.ygq.feedly.controller;

import com.ygq.feedly.common.Result;
import com.ygq.feedly.service.CommentService;
import com.ygq.feedly.vo.CommentPublishRequest;
import com.ygq.feedly.vo.CommentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 发表评论
     */
    @PostMapping("/publish")
    public Result<Long> publishComment(@RequestBody @Valid CommentPublishRequest request,
                                       @RequestParam(required = false) Long userId) {
        if (userId == null) {
            userId = 1L;
        }
        Long commentId = commentService.publishComment(request.getFeedId(), userId, request.getContent());
        return Result.success(commentId);
    }

    /**
     * 查询评论列表
     */
    @GetMapping("/list/{feedId}")
    public Result<List<CommentVO>> getComments(@PathVariable Long feedId,
                                               @RequestParam(required = false, defaultValue = "1") Integer page,
                                               @RequestParam(required = false, defaultValue = "20") Integer size) {
        List<CommentVO> comments = commentService.getComments(feedId, page, size);
        return Result.success(comments);
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/{commentId}")
    public Result<String> deleteComment(@PathVariable Long commentId,
                                        @RequestParam(required = false) Long userId) {
        if (userId == null) {
            userId = 1L;
        }
        commentService.deleteComment(commentId, userId);
        return Result.success("删除评论成功");
    }

    /**
     * 获取评论总数
     */
    @GetMapping("/count/{feedId}")
    public Result<Integer> getCommentCount(@PathVariable Long feedId) {
        int count = commentService.getCommentCount(feedId);
        return Result.success(count);
    }
}