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

    // ── 软挑战（图形验证码）相关 ─────────────────────────────────────────────────
    // 失败计数达到该阈值（但未到 MAX_FAILURES）时，要求先通过图形验证码
    private static final int      CHALLENGE_THRESHOLD     = 2;
    // 验证码连续猜错达到该次数时，触发一次轻量冷却（独立于账户硬锁，互不污染）
    private static final int      CAPTCHA_FAIL_LIMIT      = 3;
    private static final Duration CAPTCHA_COOLDOWN        = Duration.ofMinutes(1);
    private static final String   CAPTCHA_FAIL_PREFIX     = "auth:captcha:fail:";
    private static final String   CAPTCHA_COOLDOWN_PREFIX = "auth:captcha:cooldown:";

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

    /**
     * 是否需要先通过图形验证码挑战：密码失败计数已达到 {@link #CHALLENGE_THRESHOLD}（但尚未到硬锁阈值）。
     */
    public boolean needsChallenge(String identifier) {
        String count = redisTemplate.opsForValue().get(FAIL_PREFIX + identifier);
        return count != null && Long.parseLong(count) >= CHALLENGE_THRESHOLD;
    }

    /**
     * 命中验证码连错冷却则直接短路，避免在冷却期内继续消耗验证码挑战。
     */
    public void checkCaptchaCooldown(String identifier) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(CAPTCHA_COOLDOWN_PREFIX + identifier))) {
            throw new BizException(AuthResultCodeEnum.CAPTCHA_FAIL_TOO_MANY);
        }
    }

    /**
     * 记录一次验证码猜错。与密码失败计数完全独立的 key 空间，互不污染。
     *
     * @return true=本次已触顶并进入冷却，false=尚未触顶
     */
    public boolean recordCaptchaFailure(String identifier) {
        String failKey = CAPTCHA_FAIL_PREFIX + identifier;
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (Long.valueOf(1L).equals(count)) {
            redisTemplate.expire(failKey, FAIL_WINDOW);
        }
        if (count != null && count >= CAPTCHA_FAIL_LIMIT) {
            redisTemplate.opsForValue().set(CAPTCHA_COOLDOWN_PREFIX + identifier, "1", CAPTCHA_COOLDOWN);
            redisTemplate.delete(failKey);
            return true;
        }
        return false;
    }

    public void clearCaptchaFailure(String identifier) {
        redisTemplate.delete(CAPTCHA_FAIL_PREFIX + identifier);
    }
}