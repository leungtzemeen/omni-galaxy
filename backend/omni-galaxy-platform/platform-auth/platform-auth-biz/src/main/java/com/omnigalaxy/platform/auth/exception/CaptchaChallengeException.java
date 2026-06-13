package com.omnigalaxy.platform.auth.exception;

import com.omnigalaxy.common.captcha.dto.CaptchaChallengeResponse;
import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.common.core.result.IResultCode;
import lombok.Getter;

/**
 * 密码登录软挑战异常：在 {@link BizException} 基础上附带"下一次可用挑战"，
 * 由 {@code AuthController} 内的局部异常处理器拦截，随响应体一并下发给前端，
 * 不影响全局 {@code GlobalExceptionHandler} 对其他异常的处理契约。
 */
@Getter
public class CaptchaChallengeException extends BizException {

    private final CaptchaChallengeResponse nextChallenge;

    public CaptchaChallengeException(IResultCode resultCode, CaptchaChallengeResponse nextChallenge) {
        super(resultCode);
        this.nextChallenge = nextChallenge;
    }
}