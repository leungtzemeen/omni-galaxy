package com.omnigalaxy.platform.auth.controller;

import com.omnigalaxy.common.captcha.dto.CaptchaChallengeResponse;
import com.omnigalaxy.common.captcha.manager.HumanVerificationManager;
import com.omnigalaxy.common.core.result.IResultCode;
import com.omnigalaxy.common.core.result.Result;
import com.omnigalaxy.platform.auth.api.dto.LoginResponse;
import com.omnigalaxy.platform.auth.dto.OtpLoginRequest;
import com.omnigalaxy.platform.auth.dto.PasswordLoginRequest;
import com.omnigalaxy.platform.auth.dto.PasswordRegisterRequest;
import com.omnigalaxy.platform.auth.dto.SendCodeRequest;
import com.omnigalaxy.platform.auth.exception.CaptchaChallengeException;
import com.omnigalaxy.platform.auth.service.AuthCodeService;
import com.omnigalaxy.platform.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "认证中心", description = "登录、注册、验证码")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthCodeService          authCodeService;
    private final AuthService              authService;
    private final HumanVerificationManager humanVerificationManager;
    private final MessageSource            messageSource;

    @Operation(summary = "发送验证码",
               description = "支持 PHONE / EMAIL；1 分钟冷却，5 分钟有效期。开发模式下验证码打印至日志。")
    @PostMapping("/code/send")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        authCodeService.sendCode(request);
        return Result.success();
    }

    @Operation(summary = "OTP 登录 / 注册",
               description = "手机号或邮箱 + 验证码一键登注。验证码通过后自动判断新老用户，新用户自动注册。")
    @PostMapping("/login/otp")
    public Result<LoginResponse> loginByOtp(@Valid @RequestBody OtpLoginRequest request) {
        return Result.success(authService.loginByOtp(request));
    }

    @Operation(summary = "账密注册",
               description = "手机号或邮箱 + 密码 + OTP 验证码注册新账号。注册成功后自动登录并签发 Token。")
    @PostMapping("/register/password")
    public Result<LoginResponse> registerByPassword(@Valid @RequestBody PasswordRegisterRequest request) {
        return Result.success(authService.registerByPassword(request));
    }

    @Operation(summary = "密码登录",
               description = "手机号或邮箱 + 密码登录。5 分钟内连续输错 5 次将锁定 15 分钟；"
                            + "连续输错 2 次后需先完成图形验证码挑战。")
    @PostMapping("/login/password")
    public Result<LoginResponse> loginByPassword(@Valid @RequestBody PasswordLoginRequest request) {
        return Result.success(authService.loginByPassword(request));
    }

    @Operation(summary = "获取图形验证码",
               description = "用于用户主动点击“看不清，换一张”时刷新挑战；"
                            + "正常风控流程下，挑战图随错误响应内嵌返回，无需调用此接口。")
    @GetMapping("/captcha/image")
    public Result<CaptchaChallengeResponse> getCaptchaImage() {
        return Result.success(humanVerificationManager.generateChallenge());
    }

    /**
     * 局部异常处理器：仅拦截本控制器抛出的 {@link CaptchaChallengeException}，
     * 将"下一次可用挑战"随错误响应一并下发，不影响全局 GlobalExceptionHandler 对其他异常的处理契约。
     */
    @ExceptionHandler(CaptchaChallengeException.class)
    public Result<CaptchaChallengeResponse> handleCaptchaChallenge(CaptchaChallengeException e) {
        String msg = resolveMsg(e.getResultCode(), e.getArgs());
        log.warn(">>>> [Auth] 密码登录验证码风控拦截: {}", msg);
        return Result.failed(e.getResultCode().getCode(), msg, e.getNextChallenge());
    }

    private String resolveMsg(IResultCode resultCode, Object[] args) {
        if (resultCode.getClass().isEnum()) {
            String key = "code." + ((Enum<?>) resultCode).name();
            return messageSource.getMessage(key, args, resultCode.getMsg(), LocaleContextHolder.getLocale());
        }
        return resultCode.getMsg();
    }
}