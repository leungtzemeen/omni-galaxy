package com.omnigalaxy.platform.auth.component;

import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.platform.auth.api.result.AuthResultCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final int      MAX_FAILURES = 5;
    private static final Duration FAIL_WINDOW  = Duration.ofMinutes(5);
    private static final Duration BAN_DURATION = Duration.ofMinutes(15);
    private static final String   FAIL_PREFIX  = "auth:pwd:fail:";
    private static final String   BAN_PREFIX   = "auth:pwd:ban:";

    private final StringRedisTemplate redisTemplate;

    public void checkAndThrowIfBanned(String identifier) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(BAN_PREFIX + identifier))) {
            throw new BizException(AuthResultCodeEnum.ACCOUNT_LOCKED);
        }
    }

    public void recordFailure(String identifier) {
        String failKey = FAIL_PREFIX + identifier;
        Long count = redisTemplate.opsForValue().increment(failKey);
        // TTL 仅在首次写入时设置，保证固定窗口从第一次失败起算
        if (Long.valueOf(1L).equals(count)) {
            redisTemplate.expire(failKey, FAIL_WINDOW);
        }
        if (count != null && count >= MAX_FAILURES) {
            redisTemplate.opsForValue().set(BAN_PREFIX + identifier, "1", BAN_DURATION);
            redisTemplate.delete(failKey);
        }
    }

    public void clearFailures(String identifier) {
        redisTemplate.delete(FAIL_PREFIX + identifier);
    }
}