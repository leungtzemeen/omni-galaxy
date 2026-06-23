package com.omnigalaxy.platform.auth.service;

import com.omnigalaxy.platform.auth.api.dto.LoginResponse;
import com.omnigalaxy.platform.auth.dto.ChangePasswordRequest;
import com.omnigalaxy.platform.auth.dto.OtpLoginRequest;
import com.omnigalaxy.platform.auth.dto.PasswordLoginRequest;
import com.omnigalaxy.platform.auth.dto.PasswordRegisterRequest;

public interface AuthService {

    /**
     * OTP（一键登注）：手机号或邮箱 + 验证码。
     * 验证码通过后自动判断新老用户，新用户自动注册。
     */
    LoginResponse loginByOtp(OtpLoginRequest request);

    /** 账密注册：手机号或邮箱 + 密码 + OTP 验证码。注册成功后自动签发 Token。 */
    LoginResponse registerByPassword(PasswordRegisterRequest request);

    /** 账密登录：手机号或邮箱 + 密码，含防爆破拦截。 */
    LoginResponse loginByPassword(PasswordLoginRequest request);

    /** 退出登录：将传入的 Token 加入 Redis 黑名单，TTL 等于该 Token 的剩余有效期。 */
    void logout(String token);

    /**
     * 修改密码：OTP 验证身份归属 + 校验 identifier 与当前账号一致，
     * 更新密码哈希后触发全域熔断，使该用户所有存量 Token 立即失效。
     *
     * @param userId  来自网关透传 X-User-Id，已由 JWT 鉴权保证合法性
     * @param request 包含 identifier、OTP 验证码、新密码
     */
    void changePassword(Long userId, ChangePasswordRequest request);
}