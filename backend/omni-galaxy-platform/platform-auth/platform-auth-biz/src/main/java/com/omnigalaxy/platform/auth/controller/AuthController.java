package com.omnigalaxy.platform.auth.controller;

import com.omnigalaxy.common.core.result.Result;
import com.omnigalaxy.platform.auth.api.dto.LoginResponse;
import com.omnigalaxy.platform.auth.dto.OtpLoginRequest;
import com.omnigalaxy.platform.auth.dto.PasswordLoginRequest;
import com.omnigalaxy.platform.auth.dto.SendCodeRequest;
import com.omnigalaxy.platform.auth.service.AuthCodeService;
import com.omnigalaxy.platform.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证中心", description = "登录、注册、验证码")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthCodeService authCodeService;
    private final AuthService     authService;

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

    @Operation(summary = "密码登录",
               description = "账号密码登录。待 Phase 2 实现：集成 BCrypt 校验、账号锁定策略等。")
    @PostMapping("/login/password")
    public Result<LoginResponse> loginByPassword(@Valid @RequestBody PasswordLoginRequest request) {
        return Result.success(authService.loginByPassword(request));
    }
}
