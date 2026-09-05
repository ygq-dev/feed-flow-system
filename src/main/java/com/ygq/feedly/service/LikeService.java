package com.ygq.feedly.service;

import com.ygq.feedly.common.BusinessException;
import com.ygq.feedly.common.CodeMsg;
import com.ygq.feedly.entity.FeedDetail;
import com.ygq.feedly.mapper.FeedDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final RedisTemplate<String, String> redisTemplate;
    private final FeedDetailMapper feedDetailMapper;

    private static final String LIKE_KEY_PREFIX = "like:feed:";

    /**
     * 点赞
     */
    @Transactional(rollbackFor = Exception.class)
    public void like(Long feedId, Long userId) {
        FeedDetail feed = feedDetailMapper.selectById(feedId);
        if (feed == null || feed.getIsDeleted() == 1) {
            throw new BusinessException(CodeMsg.FEED_NOT_FOUND);
        }

        String likeKey = LIKE_KEY_PREFIX + feedId;
        Long added = redisTemplate.opsForSet().add(likeKey, String.valueOf(userId));

        if (added == null || added == 0) {
            throw new BusinessException(CodeMsg.ALREADY_LIKED);
        }

        // 原子更新：UPDATE feed_detail SET like_count = like_count + 1 WHERE id = #{feedId}
        feedDetailMapper.incrementLikeCount(feedId);

        redisTemplate.delete("feed:" + feedId);
        log.info("点赞成功: feedId={}, userId={}", feedId, userId);
    }
//    @Transactional(rollbackFor = Exception.class)
//    public void like(Long feedId, Long userId) {
//        // 1. 检查动态是否存在
//        FeedDetail feed = feedDetailMapper.selectById(feedId);
//        if (feed == null || feed.getIsDeleted() == 1) {
//            throw new BusinessException(CodeMsg.FEED_NOT_FOUND);
//        }
//
//        String likeKey = LIKE_KEY_PREFIX + feedId;
//
//        // 2. 使用 Redis Set 的 SADD（原子操作，自动去重）
//        Long added = redisTemplate.opsForSet().add(likeKey, String.valueOf(userId));
//
//        if (added == null || added == 0) {
//            // 已经点过赞了
//            throw new BusinessException(CodeMsg.ALREADY_LIKED);
//        }
//
//        // 3. 更新数据库的 like_count（事务保证最终一致性）
//        feed.setLikeCount(feed.getLikeCount() + 1);
//        feedDetailMapper.updateById(feed);
//
//        // 4. 更新 Redis 缓存中的 like_count
//        updateFeedCache(feedId, feed.getLikeCount());
//
//        log.info("点赞成功: feedId={}, userId={}", feedId, userId);
//    }

    /**
     * 取消点赞
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlike(Long feedId, Long userId) {
        String likeKey = LIKE_KEY_PREFIX + feedId;

        // 1. 从 Set 中移除用户
        Long removed = redisTemplate.opsForSet().remove(likeKey, String.valueOf(userId));

        if (removed == null || removed == 0) {
            throw new BusinessException(CodeMsg.NOT_LIKED);
        }

        // 2. 更新数据库的 like_count
        FeedDetail feed = feedDetailMapper.selectById(feedId);
        if (feed != null && feed.getIsDeleted() == 0) {
            int newCount = Math.max(0, feed.getLikeCount() - 1);
            feed.setLikeCount(newCount);
            feedDetailMapper.updateById(feed);
            updateFeedCache(feedId, newCount);
        }

        log.info("取消点赞成功: feedId={}, userId={}", feedId, userId);
    }

    /**
     * 查询当前用户是否已点赞
     */
    public boolean isLiked(Long feedId, Long userId) {
        String likeKey = LIKE_KEY_PREFIX + feedId;
        Boolean isMember = redisTemplate.opsForSet().isMember(likeKey, String.valueOf(userId));
        return Boolean.TRUE.equals(isMember);
    }

    /**
     * 获取点赞总数
     */
    public Long getLikeCount(Long feedId) {
        String likeKey = LIKE_KEY_PREFIX + feedId;
        Long size = redisTemplate.opsForSet().size(likeKey);
        return size != null ? size : 0L;
    }

    /**
     * 更新 Redis 缓存中的 like_count
     */
    private void updateFeedCache(Long feedId, int likeCount) {
        String cacheKey = "feed:" + feedId;
        // 简单更新：直接删除缓存，下次查询时重新加载
        // 更高效的方式：从缓存中读取 JSON，修改 like_count 字段再写回
        // 但为了保持一致性，这里采用删除缓存的方式
        redisTemplate.delete(cacheKey);
    }
}