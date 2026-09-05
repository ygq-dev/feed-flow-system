package com.ygq.feedly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ygq.feedly.entity.FeedDetail;
import com.ygq.feedly.entity.UserInfo;
import com.ygq.feedly.entity.UserRelation;
import com.ygq.feedly.mapper.FeedDetailMapper;
import com.ygq.feedly.mapper.UserInfoMapper;
import com.ygq.feedly.mapper.UserRelationMapper;
import com.ygq.feedly.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataGenerator {

    private final UserInfoMapper userInfoMapper;
    private final UserRelationMapper userRelationMapper;
    private final FeedDetailMapper feedDetailMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final RedisTemplate<String, String> redisTemplate;

    private final Random random = new Random();

    private static final int USER_COUNT = 10000;
    private static final int FEED_COUNT = 50000;
    private static final int MAX_INBOX_SIZE = 2000;

    /**
     * 生成测试数据（压测前调用一次）
     */
    public void generateTestData() {
        long startTime = System.currentTimeMillis();
        log.info("========== 开始生成测试数据 ==========");

        log.info("生成 {} 个用户...", USER_COUNT);
        generateUsers();

        log.info("生成关注关系...");
        generateFollows();

        log.info("生成 {} 条动态...", FEED_COUNT);
        generateFeeds();

        log.info("预热收件箱...");
        warmupInboxes();

        long cost = System.currentTimeMillis() - startTime;
        log.info("========== 测试数据生成完成，耗时 {} ms ==========", cost);
    }

    private void generateUsers() {
        String[] avatars = {
                "https://i.pravatar.cc/150?img=1",
                "https://i.pravatar.cc/150?img=2",
                "https://i.pravatar.cc/150?img=3",
                "https://i.pravatar.cc/150?img=4",
                "https://i.pravatar.cc/150?img=5"
        };

        int batchSize = 500;
        List<UserInfo> batch = new ArrayList<>(batchSize);

        for (int i = 1; i <= USER_COUNT; i++) {
            UserInfo user = new UserInfo();
            user.setId((long) i);
            user.setNickname("user_" + i);
            user.setAvatar(avatars[random.nextInt(avatars.length)]);
            user.setCreateTime(LocalDateTime.now());
            batch.add(user);

            if (batch.size() >= batchSize) {
                userInfoMapper.insertBatch(batch);
                batch.clear();
                log.info("已插入用户: {} / {}", i, USER_COUNT);
            }
        }
        if (!batch.isEmpty()) {
            userInfoMapper.insertBatch(batch);
        }
    }

    private void generateFollows() {
        // 每人关注 5~20 人，总共约 10 万条
        int batchSize = 500;
        List<UserRelation> batch = new ArrayList<>(batchSize);
        int inserted = 0;

        for (long userId = 1; userId <= USER_COUNT; userId++) {
            int followCount = 5 + random.nextInt(16); // 5~20
            Set<Long> followees = new HashSet<>();

            while (followees.size() < followCount) {
                long target = random.nextInt(USER_COUNT) + 1;
                if (target != userId) {
                    followees.add(target);
                }
            }

            for (Long followeeId : followees) {
                UserRelation rel = new UserRelation();
                rel.setFollowerId(userId);
                rel.setFolloweeId(followeeId);
                rel.setCreateTime(LocalDateTime.now());
                rel.setIsDeleted(0);
                batch.add(rel);

                if (batch.size() >= batchSize) {
                    // INSERT IGNORE 避免唯一键冲突
                    userRelationMapper.insertIgnoreBatch(batch);
                    inserted += batch.size();
                    batch.clear();
                }
            }

            if (userId % 1000 == 0) {
                log.info("生成关注关系进度: 用户 {} / {}", userId, USER_COUNT);
            }
        }
        if (!batch.isEmpty()) {
            userRelationMapper.insertIgnoreBatch(batch);
            inserted += batch.size();
        }
        log.info("关注关系生成完成，共 {} 条", inserted);
    }

    private void generateFeeds() {
        String[] contents = {
                "今天天气真好，阳光明媚！",
                "刚刚吃完午饭，好撑啊",
                "分享一首好听的歌",
                "周末去爬山了，风景太美了",
                "新买的一本书到了，准备开始读",
                "今天工作很顺利，开心！",
                "晚上和朋友聚餐，吃了火锅",
                "开始健身打卡第30天",
                "周末去了海边",
                "今天是个好日子"
        };

        int batchSize = 500;
        List<FeedDetail> batch = new ArrayList<>(batchSize);

        for (int i = 1; i <= FEED_COUNT; i++) {
            long feedId = idGenerator.nextId();
            long userId = random.nextInt(USER_COUNT) + 1;

            FeedDetail feed = new FeedDetail();
            feed.setId(feedId);
            feed.setUserId(userId);
            feed.setContent(contents[random.nextInt(contents.length)]);
            feed.setImages("[\"https://example.com/img/" + (random.nextInt(50) + 1) + ".jpg\"]");
            feed.setVideoUrl(null);
            feed.setVisibleRange(random.nextInt(3));
            feed.setLikeCount(random.nextInt(100));
            feed.setCommentCount(random.nextInt(20));
            feed.setForwardCount(random.nextInt(10));
            feed.setCreateTime(LocalDateTime.now().minusDays(random.nextInt(30)));
            feed.setIsDeleted(0);
            batch.add(feed);

            if (batch.size() >= batchSize) {
                feedDetailMapper.insertBatch(batch);
                batch.clear();
            }

            if (i % 5000 == 0) {
                log.info("已插入动态: {} / {}", i, FEED_COUNT);
            }
        }
        if (!batch.isEmpty()) {
            feedDetailMapper.insertBatch(batch);
        }
    }

    private void warmupInboxes() {
        // 批量预热：每个用户的收件箱写入关注者的最近动态
        int processed = 0;

        for (long userId = 1; userId <= USER_COUNT; userId++) {
            List<Long> followeeIds = userRelationMapper.selectFolloweeIds(userId);
            if (followeeIds.isEmpty()) {
                processed++;
                continue;
            }

            // 只取前 50 个关注者的动态
            List<Long> topFollowees = followeeIds.stream().limit(50).toList();
            List<FeedDetail> feeds = feedDetailMapper.selectByUserIds(topFollowees, 200);

            if (!feeds.isEmpty()) {
                String inboxKey = "inbox:" + userId;
                // 使用 Pipeline 批量写入
                redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    byte[] keyBytes = inboxKey.getBytes();
                    for (FeedDetail feed : feeds) {
                        connection.zAdd(keyBytes, feed.getId(),
                                String.valueOf(feed.getId()).getBytes());
                    }
                    // 裁剪
                    connection.zRemRange(keyBytes, 0, -(MAX_INBOX_SIZE + 1));
                    return null;
                });
            }

            processed++;
            if (processed % 1000 == 0) {
                log.info("已预热收件箱: {} / {}", processed, USER_COUNT);
            }
        }
    }
}