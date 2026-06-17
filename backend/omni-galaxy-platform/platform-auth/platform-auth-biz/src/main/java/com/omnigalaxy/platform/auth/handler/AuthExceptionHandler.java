package com.omnigalaxy.platform.auth.handler;

import com.omnigalaxy.common.captcha.dto.CaptchaChallengeResponse;
import com.omnigalaxy.common.core.result.Result;
import com.omnigalaxy.common.core.result.ResultCodeMessageResolver;
import com.omnigalaxy.platform.auth.controller.AuthController;
import com.omnigalaxy.platform.auth.exception.CaptchaChallengeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Auth 模块局部异常处理器。
 *
 * <p>通过 {@code assignableTypes} 将作用域锁定在 {@link AuthController}，
 * 不干扰全局 GlobalExceptionHandler 的处理契约。
 * 专责处理需要随响应下发额外数据（如验证码挑战图）的 Auth 域异常。
 */
@Slf4j
@RestControllerAdvice(assignableTypes = AuthController.class)
@RequiredArgsConstructor
public class AuthExceptionHandler {

    private final ResultCodeMessageResolver resultCodeMessageResolver;

    /**
     * 密码登录触发验证码风控时，将"下一次可用挑战"内嵌在错误响应体中一并下发，
     * 免去客户端额外调用 GET /auth/captcha/image 的来回。
     */
    @ExceptionHandler(CaptchaChallengeException.class)
    public Result<CaptchaChallengeResponse> handleCaptchaChallenge(CaptchaChallengeException e) {
        String msg = resultCodeMessageResolver.resolve(e.getResultCode(), e.getArgs());
        log.warn(">>>> [Auth] 密码登录验证码风控拦截: {}", msg);
        return Result.failed(e.getResultCode().getCode(), msg, e.getNextChallenge());
    }
}