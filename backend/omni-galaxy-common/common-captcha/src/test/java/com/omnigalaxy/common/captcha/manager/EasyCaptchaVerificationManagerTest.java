package com.omnigalaxy.common.captcha.manager;

import com.omnigalaxy.common.captcha.dto.CaptchaChallengeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EasyCaptchaVerificationManager} 单元测试：聚焦 GETDEL 原子销毁语义下的并发与校验分支。
 */
@ExtendWith(MockitoExtension.class)
class EasyCaptchaVerificationManagerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private EasyCaptchaVerificationManager manager;

    @BeforeEach
    void setUp() {
        manager = new EasyCaptchaVerificationManager(redisTemplate);
    }

    @Test
    void verify_concurrentRequestAlreadyConsumedChallenge_returnsFalse() {
        // 模拟高并发下另一请求已先行执行 GETDEL，本次拿到 null，命中验证码错误分支
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("captcha:challenge:shared-id")).thenReturn(null);

        assertThat(manager.verify("shared-id", "AB12")).isFalse();
    }

    @Test
    void verify_correctAnswerCaseInsensitive_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("captcha:challenge:challenge-1")).thenReturn("AB12");

        assertThat(manager.verify("challenge-1", "ab12")).isTrue();
    }

    @Test
    void verify_wrongAnswer_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("captcha:challenge:challenge-2")).thenReturn("AB12");

        assertThat(manager.verify("challenge-2", "zz99")).isFalse();
    }

    @Test
    void verify_missingChallengeIdOrAnswer_returnsFalseWithoutTouchingRedis() {
        assertThat(manager.verify(null, "AB12")).isFalse();
        assertThat(manager.verify("challenge-3", null)).isFalse();

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void generateChallenge_storesAnswerWithTtlAndReturnsImage() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        CaptchaChallengeResponse response = manager.generateChallenge();

        assertThat(response.getChallengeId()).isNotBlank();
        assertThat(response.getImageBase64()).isNotBlank();
        verify(valueOperations).set(eq("captcha:challenge:" + response.getChallengeId()), anyString(), eq(120L), eq(TimeUnit.SECONDS));
    }
}