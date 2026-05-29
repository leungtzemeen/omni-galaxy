package com.omnigalaxy.platform.auth.service;

import com.omnigalaxy.platform.auth.api.dto.LoginResponse;
import com.omnigalaxy.platform.auth.dto.OtpLoginRequest;
import com.omnigalaxy.platform.auth.dto.PasswordLoginRequest;

public interface AuthService {

    /**
     * OTP（一键登注）：手机号或邮箱 + 验证码。
     * 验证码通过后自动判断新老用户，新用户自动注册。
     */
    LoginResponse loginByOtp(OtpLoginRequest request);

    /**
     * 密码登录（待 Phase 2 实现）。
     */
    LoginResponse loginByPassword(PasswordLoginRequest request);
}
