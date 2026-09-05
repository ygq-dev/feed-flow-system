package com.ygq.feedly.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class RedisHealthChecker {

    private final AtomicInteger failures = new AtomicInteger(0);
    private volatile long circuitOpenTime = 0;

    private static final int FAILURE_THRESHOLD = 5;   // 连续失败5次 → 熔断
    private static final long RETRY_AFTER_MS = 5000;  // 每5秒放行一次探测

    private final StringRedisTemplate redisTemplate;

    public RedisHealthChecker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** timeline() 入口先问这个：false 就直接走 DB，完全不碰 Redis */
    public boolean tryRedis() {
        if (circuitOpenTime == 0) return true;                    // 正常
        if (System.currentTimeMillis() - circuitOpenTime >= RETRY_AFTER_MS) {
            circuitOpenTime = 0;                                   // 半开：放一个请求去试探
            return true;
        }
        return false;                                             // 熔断中
    }

    public void recordSuccess() { failures.set(0); }

    public void recordFailure() {
        if (failures.incrementAndGet() >= FAILURE_THRESHOLD) {
            circuitOpenTime = System.currentTimeMillis();
        }
    }

    /** 后台每 3 秒主动探测，Redis 恢复后自动闭合 */
    @Scheduled(fixedRate = 3000)
    public void probe() {
        try {
            redisTemplate.opsForValue().get("health:ping");
            recordSuccess();
        } catch (Exception e) {
            recordFailure();
        }
    }
}