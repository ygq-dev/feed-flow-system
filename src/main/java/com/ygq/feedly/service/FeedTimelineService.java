package com.ygq.feedly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ygq.feedly.entity.FeedDetail;
import com.ygq.feedly.entity.UserInfo;
import com.ygq.feedly.mapper.FeedDetailMapper;
import com.ygq.feedly.mapper.UserInfoMapper;
import com.ygq.feedly.mapper.UserRelationMapper;
import com.ygq.feedly.vo.FeedVO;
import com.ygq.feedly.vo.TimelineResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedTimelineService {

    private final RedisTemplate<String, String> redisTemplate;
    private final FeedDetailMapper feedDetailMapper;
    private final UserRelationMapper userRelationMapper;
    private final UserInfoMapper userInfoMapper;
    private final UserCacheService userCacheService;
    private final RedisHealthChecker redisHealthChecker;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 刷 Feed 流（带自动降级）
     */
    public TimelineResult getTimeline(Long userId, Long lastFeedId, Integer size) {
        if (size == null || size <= 0) {
            size = DEFAULT_PAGE_SIZE;
        }

        // 1. 先问熔断器：Redis 是否可用？
        if (redisHealthChecker.tryRedis()) {
            try {
                // 正常走 Redis 收件箱
                TimelineResult result = getTimelineFromRedis(userId, lastFeedId, size);
                redisHealthChecker.recordSuccess();   // 成功，重置失败计数
                return result;
            } catch (Exception e) {
                // Redis 操作失败（连接超时等），记一次失败
                redisHealthChecker.recordFailure();
                log.warn("Redis 操作失败，触发降级: {}", e.getMessage());
            }
        } else {
            log.debug("Redis 熔断中，直接走 DB 降级");
        }

        // 2. 降级路径（熔断打开 或 Redis 异常后统一走这里）
        return getTimelineFromDB(userId, lastFeedId, size);
    }

    /**
     * 正常模式：从 Redis 收件箱读取
     */
    private TimelineResult getTimelineFromRedis(Long userId, Long lastFeedId, Integer size) {
        String inboxKey = "inbox:" + userId;

        Set<String> feedIdStrs;
        if (lastFeedId == null) {
            feedIdStrs = redisTemplate.opsForZSet().reverseRange(inboxKey, 0, size - 1);
        } else {
            feedIdStrs = redisTemplate.opsForZSet().reverseRangeByScore(
                    inboxKey, 0, lastFeedId - 1, 0, size
            );
        }

        if (feedIdStrs == null || feedIdStrs.isEmpty()) {
            return TimelineResult.empty();
        }

        List<FeedDetail> feeds = batchGetFeeds(feedIdStrs);

        if (feeds.isEmpty()) {
            return TimelineResult.empty();
        }

        List<FeedDetail> filteredFeeds = filterByVisibility(userId, feeds);

        if (filteredFeeds.isEmpty()) {
            return TimelineResult.empty();
        }

        List<FeedVO> voList = filteredFeeds.stream()
                .map(feed -> convertToVO(feed))
                .toList();

        boolean hasMore = filteredFeeds.size() == size;
        Long newLastFeedId = filteredFeeds.isEmpty() ? null : filteredFeeds.get(filteredFeeds.size() - 1).getId();

        return new TimelineResult(voList, newLastFeedId, hasMore);
    }

    /**
     * 降级模式：从 DB 直接查询（拉模式兜底）
     */
    private TimelineResult getTimelineFromDB(Long userId, Long lastFeedId, Integer size) {
        // 1. 获取关注列表
        List<Long> followeeIds = userRelationMapper.selectFolloweeIds(userId);
        if (followeeIds == null || followeeIds.isEmpty()) {
            return getOwnFeedsFromDB(userId, lastFeedId, size);
        }

        // 2. 从 DB 批量查询这些用户的动态
        List<FeedDetail> feeds = feedDetailMapper.selectByUserIds(followeeIds, size * 2);

        if (feeds == null || feeds.isEmpty()) {
            return TimelineResult.empty();
        }

        // 3. 可见范围过滤
        List<FeedDetail> filteredFeeds = filterByVisibility(userId, feeds);

        // 4. 游标分页
        List<FeedDetail> pagedFeeds = new ArrayList<>();
        boolean hasMore = false;

        if (lastFeedId == null) {
            int end = Math.min(size, filteredFeeds.size());
            pagedFeeds = filteredFeeds.subList(0, end);
            hasMore = filteredFeeds.size() > size;
        } else {
            int startIdx = -1;
            for (int i = 0; i < filteredFeeds.size(); i++) {
                if (filteredFeeds.get(i).getId().equals(lastFeedId)) {
                    startIdx = i + 1;
                    break;
                }
            }
            if (startIdx >= 0 && startIdx < filteredFeeds.size()) {
                int end = Math.min(startIdx + size, filteredFeeds.size());
                pagedFeeds = filteredFeeds.subList(startIdx, end);
                hasMore = end < filteredFeeds.size();
            } else {
                return TimelineResult.empty();
            }
        }

        if (pagedFeeds.isEmpty()) {
            return TimelineResult.empty();
        }

        // 5. 批量获取用户信息（直接从 DB，不走缓存）
        List<Long> userIds = pagedFeeds.stream()
                .map(FeedDetail::getUserId)
                .distinct()
                .collect(Collectors.toList());
        List<UserInfo> users = userInfoMapper.selectByIds(userIds);
        Map<Long, UserInfo> userMap = users.stream()
                .collect(Collectors.toMap(UserInfo::getId, u -> u));

        // 6. 转换为 VO（降级模式，使用专用方法，不走缓存）
        List<FeedVO> voList = pagedFeeds.stream()
                .map(feed -> convertToVOWithUser(feed, userMap))
                .collect(Collectors.toList());

        Long newLastFeedId = pagedFeeds.isEmpty() ? null : pagedFeeds.get(pagedFeeds.size() - 1).getId();

        return new TimelineResult(voList, newLastFeedId, hasMore);
    }

    /**
     * 降级模式：查询自己的动态（当没有关注任何人时）
     */
    private TimelineResult getOwnFeedsFromDB(Long userId, Long lastFeedId, Integer size) {
        List<FeedDetail> feeds = feedDetailMapper.selectByUserId(userId, size * 2);
        // 过滤自己的动态（visible_range 2 也可以看到）
        // 这里的逻辑和上面类似，可以复用
        // 简化处理：直接返回
        if (feeds == null || feeds.isEmpty()) {
            return TimelineResult.empty();
        }
        List<FeedVO> voList = feeds.stream()
                .map(feed -> convertToVO(feed))
                .toList();
        return new TimelineResult(voList, feeds.isEmpty() ? null : feeds.get(feeds.size() - 1).getId(), false);
    }

    /**
     * 可见范围过滤
     */
    private List<FeedDetail> filterByVisibility(Long currentUserId, List<FeedDetail> feeds) {
        List<FeedDetail> result = new ArrayList<>();

        // 如果当前用户不存在（未登录），只返回公开动态
        if (currentUserId == null) {
            for (FeedDetail feed : feeds) {
                if (feed.getVisibleRange() == 0) {
                    result.add(feed);
                }
            }
            return result;
        }

        // 当前用户已登录，按规则过滤
        for (FeedDetail feed : feeds) {
            Integer visibleRange = feed.getVisibleRange();
            Long publisherId = feed.getUserId();

            // 公开：所有人都可见
            if (visibleRange == 0) {
                result.add(feed);
                continue;
            }

            // 仅自己：只有发布者自己可见
            if (visibleRange == 2) {
                if (currentUserId.equals(publisherId)) {
                    result.add(feed);
                }
                continue;
            }

            // 仅好友（visibleRange == 1）：检查是否是好友关系
            if (visibleRange == 1) {
                // 如果是自己的动态，当然可见
                if (currentUserId.equals(publisherId)) {
                    result.add(feed);
                    continue;
                }
                // 检查 currentUserId 是否关注了 publisherId
                boolean isFollowing = userRelationMapper.exists(currentUserId, publisherId);
                if (isFollowing) {
                    result.add(feed);
                }
                // 不是好友则不可见
            }
        }

        return result;
    }

    /**
     * 批量获取 Feed 详情（先查 Redis 缓存，未命中则查 DB）
     */
    private List<FeedDetail> batchGetFeeds(Set<String> feedIdStrs) {
        List<Long> feedIds = feedIdStrs.stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());

        // 构建缓存 Key 列表
        List<String> cacheKeys = feedIds.stream()
                .map(id -> "feed:" + id)
                .collect(Collectors.toList());

        // 批量获取
        List<String> cachedValues = redisTemplate.opsForValue().multiGet(cacheKeys);

        List<FeedDetail> result = new ArrayList<>();
        List<Long> missedIds = new ArrayList<>();

        for (int i = 0; i < cachedValues.size(); i++) {
            String cached = cachedValues.get(i);
            if (StringUtils.hasText(cached)) {
                try {
                    result.add(objectMapper.readValue(cached, FeedDetail.class));
                } catch (Exception e) {
                    missedIds.add(feedIds.get(i));
                }
            } else {
                missedIds.add(feedIds.get(i));
            }
        }

        // 未命中的从 DB 批量查询 + 回填缓存
        if (!missedIds.isEmpty()) {
            List<FeedDetail> dbFeeds = feedDetailMapper.selectByFeedIds(missedIds);
            // 批量回填缓存
            for (FeedDetail feed : dbFeeds) {
                try {
                    String json = objectMapper.writeValueAsString(feed);
                    redisTemplate.opsForValue().set("feed:" + feed.getId(), json, 7, TimeUnit.DAYS);
                } catch (Exception e) {
                    log.warn("Feed详情缓存写入失败: feedId={}", feed.getId(), e);
                }
            }
            result.addAll(dbFeeds);
        }

        result.sort((a, b) -> Long.compare(b.getId(), a.getId()));
        return result;
    }

    private FeedVO convertToVO(FeedDetail feed) {
        // 获取用户信息
        UserInfo user = userCacheService.getUser(feed.getUserId());

        FeedVO vo = new FeedVO();
        vo.setFeedId(feed.getId());
        vo.setUserId(feed.getUserId());
        vo.setNickname(user != null ? user.getNickname() : "未知用户");
        vo.setAvatar(user != null ? user.getAvatar() : null);
        vo.setContent(feed.getContent());
        vo.setImages(feed.getImages());
        vo.setVideoUrl(feed.getVideoUrl());
        vo.setLikeCount(feed.getLikeCount());
        vo.setCommentCount(feed.getCommentCount());
        vo.setCreateTime(feed.getCreateTime());
        return vo;
    }

    /**
     * 降级模式专用的 VO 转换（直接使用已查好的用户信息，不走缓存）
     */
    private FeedVO convertToVOWithUser(FeedDetail feed, Map<Long, UserInfo> userMap) {
        FeedVO vo = new FeedVO();
        vo.setFeedId(feed.getId());
        vo.setUserId(feed.getUserId());
        vo.setContent(feed.getContent());
        vo.setImages(feed.getImages());
        vo.setVideoUrl(feed.getVideoUrl());
        vo.setLikeCount(feed.getLikeCount());
        vo.setCommentCount(feed.getCommentCount());
        vo.setCreateTime(feed.getCreateTime());

        UserInfo user = userMap.get(feed.getUserId());
        vo.setNickname(user != null ? user.getNickname() : "未知用户");
        vo.setAvatar(user != null ? user.getAvatar() : null);

        return vo;
    }
}