package com.omnigalaxy.platform.auth.component;

import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.platform.auth.api.result.AuthResultCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LoginRateLimiter} 单元测试：聚焦软挑战（图形验证码）相关的判定与计数分支。
 */
@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

    private static final String IDENTIFIER = "13800000000";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new LoginRateLimiter(redisTemplate);
    }

    @Test
    void needsChallenge_failureCountReachesThreshold_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:pwd:fail:" + IDENTIFIER)).thenReturn("2");

        assertThat(rateLimiter.needsChallenge(IDENTIFIER)).isTrue();
    }

    @Test
    void needsChallenge_failureCountBelowThreshold_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:pwd:fail:" + IDENTIFIER)).thenReturn("1");

        assertThat(rateLimiter.needsChallenge(IDENTIFIER)).isFalse();
    }

    @Test
    void needsChallenge_noFailureRecord_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:pwd:fail:" + IDENTIFIER)).thenReturn(null);

        assertThat(rateLimiter.needsChallenge(IDENTIFIER)).isFalse();
    }

    @Test
    void checkCaptchaCooldown_cooldownActive_throwsCaptchaFailTooMany() {
        when(redisTemplate.hasKey("auth:captcha:cooldown:" + IDENTIFIER)).thenReturn(true);

        assertThatThrownBy(() -> rateLimiter.checkCaptchaCooldown(IDENTIFIER))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(AuthResultCodeEnum.CAPTCHA_FAIL_TOO_MANY);
    }

    @Test
    void checkCaptchaCooldown_noCooldown_doesNotThrow() {
        when(redisTemplate.hasKey("auth:captcha:cooldown:" + IDENTIFIER)).thenReturn(false);

        assertThatCode(() -> rateLimiter.checkCaptchaCooldown(IDENTIFIER)).doesNotThrowAnyException();
    }

    @Test
    void recordCaptchaFailure_firstFailure_setsTtlAndReturnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:captcha:fail:" + IDENTIFIER)).thenReturn(1L);

        boolean cooldownTriggered = rateLimiter.recordCaptchaFailure(IDENTIFIER);

        assertThat(cooldownTriggered).isFalse();
        verify(redisTemplate).expire("auth:captcha:fail:" + IDENTIFIER, Duration.ofMinutes(5));
        verify(redisTemplate, never()).delete("auth:captcha:fail:" + IDENTIFIER);
    }

    @Test
    void recordCaptchaFailure_thirdConsecutiveFailure_triggersOneMinuteCooldown() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:captcha:fail:" + IDENTIFIER)).thenReturn(3L);

        boolean cooldownTriggered = rateLimiter.recordCaptchaFailure(IDENTIFIER);

        assertThat(cooldownTriggered).isTrue();
        verify(valueOperations).set("auth:captcha:cooldown:" + IDENTIFIER, "1", Duration.ofMinutes(1));
        verify(redisTemplate).delete("auth:captcha:fail:" + IDENTIFIER);
    }

    @Test
    void clearCaptchaFailure_deletesFailureCounterKey() {
        rateLimiter.clearCaptchaFailure(IDENTIFIER);

        verify(redisTemplate).delete("auth:captcha:fail:" + IDENTIFIER);
    }
}