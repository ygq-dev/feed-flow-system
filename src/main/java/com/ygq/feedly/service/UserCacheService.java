package com.ygq.feedly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ygq.feedly.entity.UserInfo;
import com.ygq.feedly.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final UserInfoMapper userInfoMapper;
    private final ObjectMapper objectMapper;

    private static final String USER_CACHE_PREFIX = "user:";
    private static final long USER_CACHE_TTL = 3600; // 1小时

    /**
     * 批量获取用户信息（先缓存后DB）
     */
    public List<UserInfo> batchGetUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 去重
        List<Long> distinctUserIds = userIds.stream().distinct().collect(Collectors.toList());

        List<UserInfo> result = new ArrayList<>();
        List<Long> missedIds = new ArrayList<>();

        // 1. 从缓存批量读取
        for (Long userId : distinctUserIds) {
            String cacheKey = USER_CACHE_PREFIX + userId;
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                try {
                    UserInfo user = objectMapper.readValue(cached, UserInfo.class);
                    result.add(user);
                } catch (Exception e) {
                    log.warn("用户缓存反序列化失败: userId={}", userId, e);
                    missedIds.add(userId);
                }
            } else {
                missedIds.add(userId);
            }
        }

        // 2. 未命中的从DB查询
        if (!missedIds.isEmpty()) {
            List<UserInfo> dbUsers = userInfoMapper.selectByIds(missedIds);
            // 回填缓存
            for (UserInfo user : dbUsers) {
                try {
                    String json = objectMapper.writeValueAsString(user);
                    redisTemplate.opsForValue().set(USER_CACHE_PREFIX + user.getId(), json, USER_CACHE_TTL, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("用户缓存写入失败: userId={}", user.getId(), e);
                }
                result.add(user);
            }
        }

        return result;
    }

    /**
     * 获取单个用户信息
     */
    public UserInfo getUser(Long userId) {
        List<Long> ids = new ArrayList<>();
        ids.add(userId);
        List<UserInfo> users = batchGetUsers(ids);
        return users.isEmpty() ? null : users.get(0);
    }
}