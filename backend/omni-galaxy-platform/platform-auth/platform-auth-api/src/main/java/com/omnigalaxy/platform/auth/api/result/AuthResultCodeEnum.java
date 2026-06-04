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
    IDENTIFIER_OTP_REGISTERED(409, "该手机/邮箱已通过验证码注册，请使用验证码登录后在账户设置中绑定密码"),

    // ── OAuth / 小程序绑定流程 ─────────────────────────────────────────────────
    BIND_TICKET_EXPIRED(400, "绑定凭证已过期或无效，请重新发起登录"),
    BIND_IDENTIFIER_MISMATCH(400, "绑定凭证与授权信息不符，请勿篡改请求"),
    SOCIAL_ACCOUNT_BOUND_TO_ANOTHER(409, "该社交账号已绑定至其他账号，如需更换请先在原账号中解绑"),
    MULTIPLE_ACCOUNT_CONFLICT(409, "当前社交账号关联的凭证分属多个不同账号，无法自动合并，请联系客服处理"),
    ACCOUNT_NOT_FOUND(404, "账号不存在"),

    // ── OAuth state / CSRF ────────────────────────────────────────────────────
    INVALID_OAUTH_STATE(400, "OAuth state 无效或已过期，请重新发起授权"),
    OAUTH_CODE_EXCHANGE_FAILED(502, "与第三方平台通信失败，请稍后重试");

    private final int code;
    private final String msg;
}
