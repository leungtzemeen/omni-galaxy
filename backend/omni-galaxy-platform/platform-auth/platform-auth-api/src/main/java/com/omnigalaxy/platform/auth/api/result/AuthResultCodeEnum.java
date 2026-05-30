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
    PASSWORD_LOGIN_NOT_IMPL(501, "密码登录功能待 Phase 2 实现");

    private final int code;
    private final String msg;
}
