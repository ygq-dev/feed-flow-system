package com.ygq.feedly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ygq.feedly.common.BusinessException;
import com.ygq.feedly.common.CodeMsg;
import com.ygq.feedly.entity.FeedDetail;
import com.ygq.feedly.mapper.FeedDetailMapper;
import com.ygq.feedly.mq.FanoutMessage;
import com.ygq.feedly.util.SnowflakeIdGenerator;
import com.ygq.feedly.vo.FeedPublishRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.ygq.feedly.config.RabbitMQConfig.FANOUT_EXCHANGE;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedPublishService {

    private final FeedDetailMapper feedDetailMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final RedisTemplate<String, String> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${feedly.feed.max-inbox-size:2000}")
    private int maxInboxSize;

    /**
     * 发布动态
     */
    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long userId, FeedPublishRequest request) {
        // 1. 生成 feedId
        long feedId = idGenerator.nextId();

        // 2. 构建实体
        FeedDetail feed = new FeedDetail();
        feed.setId(feedId);
        feed.setUserId(userId);
        feed.setContent(request.getContent());
        feed.setImages(request.getImages() != null ? toJson(request.getImages()) : null);
        feed.setVideoUrl(request.getVideoUrl());
        feed.setVisibleRange(request.getVisibleRange() != null ? request.getVisibleRange() : 0);
        feed.setLikeCount(0);
        feed.setCommentCount(0);
        feed.setForwardCount(0);
        feed.setCreateTime(LocalDateTime.now());
        feed.setIsDeleted(0);

        // 3. 写入 MySQL
        feedDetailMapper.insert(feed);

        // 4. 写入 Redis 详情缓存（预热）
        String cacheKey = "feed:" + feedId;
        try {
            String json = objectMapper.writeValueAsString(feed);
            redisTemplate.opsForValue().set(cacheKey, json, 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Feed详情缓存写入失败: feedId={}", feedId, e);
        }

        // 5. 写入自己的收件箱（自己发自己能看到）
        String inboxKey = "inbox:" + userId;
        redisTemplate.opsForZSet().add(inboxKey, String.valueOf(feedId), feedId);
        // 裁剪收件箱（保留最近 N 条）
        redisTemplate.opsForZSet().removeRange(inboxKey, 0, -(maxInboxSize + 1));

        // 6. 发送 MQ 消息，异步推送给粉丝
        FanoutMessage message = new FanoutMessage(
                feedId,
                userId,
                System.currentTimeMillis(),
                feed.getVisibleRange()
        );
        rabbitTemplate.convertAndSend(FANOUT_EXCHANGE, "", message);
        log.info("发布动态成功: feedId={}, userId={}", feedId, userId);

        return feedId;
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new RuntimeException("序列化失败", e);
        }
    }
}