package com.omnigalaxy.common.captcha.manager;

import com.omnigalaxy.common.captcha.dto.CaptchaChallengeResponse;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于 easy-captcha 的图形验证码实现。
 *
 * <p>Key 设计：{@code captcha:challenge:{challengeId}}，TTL 2 分钟，存储验证码明文答案。
 * {@link #verify} 使用 Redis 原子 GETDEL（{@code getAndDelete}），避免"先 GET 比对再 DELETE"
 * 在高并发下的 TOCTOU 竞态——同一 challengeId 的并发校验请求中，至多一个能拿到非空答案。
 */
@Slf4j
@RequiredArgsConstructor
public class EasyCaptchaVerificationManager implements HumanVerificationManager {

    private static final long   CHALLENGE_TTL_SECONDS = 120L;
    private static final String CHALLENGE_PREFIX      = "captcha:challenge:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public CaptchaChallengeResponse generateChallenge() {
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        captcha.setCharType(Captcha.TYPE_NUM_AND_UPPER);

        String challengeId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                CHALLENGE_PREFIX + challengeId, captcha.text(), CHALLENGE_TTL_SECONDS, TimeUnit.SECONDS);

        log.info(">>>> [HumanVerification] 图形验证码挑战已生成 challengeId: {}", challengeId);
        return new CaptchaChallengeResponse(challengeId, captcha.toBase64());
    }

    @Override
    public boolean verify(String challengeId, String answer) {
        if (challengeId == null || answer == null) {
            return false;
        }
        String stored = redisTemplate.opsForValue().getAndDelete(CHALLENGE_PREFIX + challengeId);
        boolean passed = stored != null && stored.equalsIgnoreCase(answer);
        if (!passed) {
            log.warn(">>>> [HumanVerification] 图形验证码校验失败（错误或已过期）challengeId: {}", challengeId);
        }
        return passed;
    }
}