package com.omnigalaxy.common.captcha.enums;

/**
 * 验证码使用场景枚举。
 * 场景隔离确保不同业务的 OTP key 互不干扰，防止跨场景复用攻击。
 */
public enum OtpScene {
    LOGIN,
    REGISTER,
    BIND_PHONE,
    BIND_EMAIL,
    RESET_PWD
}
