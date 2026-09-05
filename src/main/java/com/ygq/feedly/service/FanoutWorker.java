package com.ygq.feedly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.ygq.feedly.config.RabbitMQConfig;
import com.ygq.feedly.mapper.UserRelationMapper;
import com.ygq.feedly.mq.FanoutMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FanoutWorker {

    private final UserRelationMapper userRelationMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_INBOX_SIZE = 2000;
    private static final int BATCH_SIZE = 1000;

    @RabbitListener(queues = RabbitMQConfig.FANOUT_QUEUE)
    public void onMessage(Message message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            String json = new String(message.getBody());
            FanoutMessage msg = objectMapper.readValue(json, FanoutMessage.class);

            Long feedId = msg.getFeedId();
            Long userId = msg.getUserId();

            int offset = 0;
            int totalFans = 0;

            while (true) {
                List<Long> followerIds = userRelationMapper.selectFollowerIds(userId, offset, BATCH_SIZE);
                if (followerIds == null || followerIds.isEmpty()) {
                    break;
                }
                totalFans += followerIds.size();

                // 使用 Pipeline 批量写入
                redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    for (Long followerId : followerIds) {
                        String inboxKey = "inbox:" + followerId;
                        // ZADD
                        connection.zAdd(inboxKey.getBytes(), feedId, String.valueOf(feedId).getBytes());
                        // ZREMRANGEBYRANK 裁剪
                        connection.zRemRange(inboxKey.getBytes(), 0, -(MAX_INBOX_SIZE + 1));
                    }
                    return null;
                });

                if (followerIds.size() < BATCH_SIZE) {
                    break;
                }
                offset += BATCH_SIZE;
            }

            log.info("写扩散完成: feedId={}, 粉丝数={}", feedId, totalFans);
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("写扩散失败", e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

//    @RabbitListener(queues = RabbitMQConfig.FANOUT_QUEUE)
//    public void onMessage(Message message, Channel channel,
//                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
//        try {
//            // 1. 解析消息
//            String json = new String(message.getBody());
//            FanoutMessage msg = objectMapper.readValue(json, FanoutMessage.class);
//
//            Long feedId = msg.getFeedId();
//            Long userId = msg.getUserId();
//
//            log.info("收到写扩散消息: feedId={}, userId={}", feedId, userId);
//
//            // 2. 分页查询该用户的粉丝列表
//            int offset = 0;
//            int totalFans = 0;
//
//            while (true) {
//                List<Long> followerIds = userRelationMapper.selectFollowerIds(userId, offset, BATCH_SIZE);
//                if (followerIds == null || followerIds.isEmpty()) {
//                    break;
//                }
//
//                totalFans += followerIds.size();
//
//                // 3. 批量写入收件箱（使用 Redis Pipeline）
//                for (Long followerId : followerIds) {
//                    String inboxKey = "inbox:" + followerId;
//                    // 写入 feedId（score 用 feedId，因为雪花ID是时间有序的）
//                    redisTemplate.opsForZSet().add(inboxKey, String.valueOf(feedId), feedId);
//                    // 裁剪收件箱，保留最近 MAX_INBOX_SIZE 条
//                    redisTemplate.opsForZSet().removeRange(inboxKey, 0, -(MAX_INBOX_SIZE + 1));
//                }
//
//                log.info("已推送 {} 条到粉丝收件箱，当前批次: {}", followerIds.size(), offset / BATCH_SIZE + 1);
//
//                if (followerIds.size() < BATCH_SIZE) {
//                    break;
//                }
//                offset += BATCH_SIZE;
//            }
//
//            log.info("写扩散完成: feedId={}, 粉丝数={}", feedId, totalFans);
//
//            // 4. ACK 确认消息
//            channel.basicAck(deliveryTag, false);
//
//        } catch (Exception e) {
//            log.error("写扩散失败", e);
//            // 重试：nack 并重新入队
//            channel.basicNack(deliveryTag, false, true);
//        }
//    }
}