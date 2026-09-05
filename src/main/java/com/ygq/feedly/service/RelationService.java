package com.ygq.feedly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ygq.feedly.common.BusinessException;
import com.ygq.feedly.common.CodeMsg;
import com.ygq.feedly.entity.UserRelation;
import com.ygq.feedly.mapper.FeedDetailMapper;
import com.ygq.feedly.mapper.UserRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelationService {

    private final UserRelationMapper userRelationMapper;
    private final FeedDetailMapper feedDetailMapper;
    private final RedisTemplate<String, String> redisTemplate;

    private static final int PULL_SIZE = 20;

    @Transactional(rollbackFor = Exception.class)
    public void follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new BusinessException(CodeMsg.CANNOT_FOLLOW_SELF);
        }

        LambdaQueryWrapper<UserRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRelation::getFollowerId, followerId)
                .eq(UserRelation::getFolloweeId, followeeId);
        UserRelation existing = userRelationMapper.selectOne(wrapper);

        if (existing != null) {
            if (existing.getIsDeleted() == 0) {
                throw new BusinessException(CodeMsg.ALREADY_FOLLOWED);
            }
            // 恢复关注
            existing.setIsDeleted(0);
            userRelationMapper.updateById(existing);
            log.info("恢复关注成功: {} -> {}", followerId, followeeId);
            pullRecentFeedsToInbox(followerId, followeeId, PULL_SIZE);
            return;
        }

        UserRelation relation = new UserRelation();
        relation.setFollowerId(followerId);
        relation.setFolloweeId(followeeId);
        relation.setCreateTime(LocalDateTime.now());
        relation.setIsDeleted(0);
        userRelationMapper.insert(relation);

        log.info("关注成功: {} -> {}", followerId, followeeId);
        pullRecentFeedsToInbox(followerId, followeeId, PULL_SIZE);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long followerId, Long followeeId) {
        LambdaQueryWrapper<UserRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRelation::getFollowerId, followerId)
                .eq(UserRelation::getFolloweeId, followeeId);
        UserRelation existing = userRelationMapper.selectOne(wrapper);

        if (existing == null) {
            throw new BusinessException(CodeMsg.NOT_FOLLOWED);
        }

        if (existing.getIsDeleted() == 1) {
            throw new BusinessException(CodeMsg.NOT_FOLLOWED);
        }

        existing.setIsDeleted(1);
        userRelationMapper.updateById(existing);
        log.info("取关成功: {} -> {}", followerId, followeeId);
    }

    private void pullRecentFeedsToInbox(Long followerId, Long followeeId, int limit) {
        List<Long> feedIds = feedDetailMapper.selectRecentFeedIdsByUserId(followeeId, limit);
        if (feedIds.isEmpty()) {
            return;
        }
        String inboxKey = "inbox:" + followerId;
        for (Long feedId : feedIds) {
            redisTemplate.opsForZSet().add(inboxKey, String.valueOf(feedId), feedId);
        }
        log.info("拉取 {} 条动态到 {} 的收件箱", feedIds.size(), followerId);
    }
}