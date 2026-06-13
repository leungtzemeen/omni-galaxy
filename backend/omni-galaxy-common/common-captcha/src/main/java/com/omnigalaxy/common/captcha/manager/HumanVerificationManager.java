package com.omnigalaxy.common.captcha.manager;

import com.omnigalaxy.common.captcha.dto.CaptchaChallengeResponse;

/**
 * 人机验证管理器。
 * 策略接口：本期实现图形验证码（{@code EasyCaptchaVerificationManager}），
 * 预留滑块/reCAPTCHA 等策略的等价扩展位，业务调用方无需感知具体实现切换。
 */
public interface HumanVerificationManager {

    /**
     * 生成一次性挑战，答案存入 Redis（短 TTL）。
     */
    CaptchaChallengeResponse generateChallenge();

    /**
     * 校验挑战答案。无论校验结果是否正确，Redis 中的挑战记录均立即销毁（防重放/防并发重复使用）。
     *
     * @return true=通过，false=错误或已过期
     */
    boolean verify(String challengeId, String answer);
}