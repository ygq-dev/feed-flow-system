package com.ygq.feedly.service;

import com.ygq.feedly.common.BusinessException;
import com.ygq.feedly.common.CodeMsg;
import com.ygq.feedly.entity.FeedComment;
import com.ygq.feedly.entity.FeedDetail;
import com.ygq.feedly.mapper.FeedCommentMapper;
import com.ygq.feedly.mapper.FeedDetailMapper;
import com.ygq.feedly.util.SnowflakeIdGenerator;
import com.ygq.feedly.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final FeedCommentMapper feedCommentMapper;
    private final FeedDetailMapper feedDetailMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final RedisTemplate<String, String> redisTemplate;

    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 发表评论
     */
    @Transactional(rollbackFor = Exception.class)
    public Long publishComment(Long feedId, Long userId, String content) {
        // 1. 检查动态是否存在
        FeedDetail feed = feedDetailMapper.selectById(feedId);
        if (feed == null || feed.getIsDeleted() == 1) {
            throw new BusinessException(CodeMsg.FEED_NOT_FOUND);
        }

        // 2. 生成评论ID
        long commentId = idGenerator.nextId();

        // 3. 插入评论
        FeedComment comment = new FeedComment();
        comment.setId(commentId);
        comment.setFeedId(feedId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setLikeCount(0);
        comment.setCreateTime(LocalDateTime.now());
        comment.setIsDeleted(0);
        feedCommentMapper.insert(comment);
        feedDetailMapper.incrementCommentCount(feedId);  // ← 原子更新
        redisTemplate.delete("feed:" + feedId);
        return commentId;
    }

    /**
     * 查询评论列表（分页）
     */
    public List<CommentVO> getComments(Long feedId, Integer page, Integer size) {
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size <= 0) {
            size = DEFAULT_PAGE_SIZE;
        }

        int offset = (page - 1) * size;

        List<FeedComment> comments = feedCommentMapper.selectByFeedId(feedId, offset, size);

        return comments.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 删除评论（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId, Long userId) {
        FeedComment comment = feedCommentMapper.selectById(commentId);
        if (comment == null || comment.getIsDeleted() == 1) {
            throw new BusinessException(CodeMsg.COMMENT_NOT_FOUND);
        }

        // 只有评论者本人可以删除
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(CodeMsg.NO_PERMISSION);
        }

        comment.setIsDeleted(1);
        feedCommentMapper.updateById(comment);
        feedDetailMapper.decrementCommentCount(comment.getFeedId());  // ← 原子更新
        redisTemplate.delete("feed:" + comment.getFeedId());
    }

    /**
     * 获取评论总数
     */
    public int getCommentCount(Long feedId) {
        return feedCommentMapper.countByFeedId(feedId);
    }

    private CommentVO convertToVO(FeedComment comment) {
        CommentVO vo = new CommentVO();
        vo.setCommentId(comment.getId());
        vo.setFeedId(comment.getFeedId());
        vo.setUserId(comment.getUserId());
        vo.setContent(comment.getContent());
        vo.setLikeCount(comment.getLikeCount());
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }
}