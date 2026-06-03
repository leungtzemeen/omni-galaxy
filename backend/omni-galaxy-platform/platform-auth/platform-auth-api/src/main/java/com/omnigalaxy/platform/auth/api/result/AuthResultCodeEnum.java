package com.omnigalaxy.platform.auth.api.result;

import com.omnigalaxy.common.core.result.IResultCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Auth 域专属业务错误码。
 * 反向桥接 key 命名规则：code.{name()}，对应 i18n/messages.properties 中的条目。
 */
@Getter
@RequiredArgsConstructor
public enum AuthResultCodeEnum implements IResultCode {

    OTP_INVALID(400, "验证码错误或已过期，请重新获取"),
    OTP_SCENE_UNSUPPORTED(400, "不支持的验证码场景"),

    PASSWORD_WRONG(401, "账号或密码错误"),
    ACCOUNT_LOCKED(429, "密码连续错误次数过多，账号已被临时锁定，请 15 分钟后重试"),
    ACCOUNT_DISABLED(403, "账号已被禁用，请联系管理员"),

    IDENTIFIER_ALREADY_REGISTERED(409, "该账号已注册，请直接登录"),
    IDENTIFIER_OTP_REGISTERED(409, "该手机/邮箱已通过验证码注册，请使用验证码登录后在账户设置中绑定密码");

    private final int code;
    private final String msg;
}
