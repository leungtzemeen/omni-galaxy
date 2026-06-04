package com.omnigalaxy.platform.auth.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 社交登录统一响应体。
 *
 * <p>前端必须先检查 {@link #status} 决定后续路由：
 * <ul>
 *   <li>{@code SUCCESS}：直接使用 accessToken 完成登录。</li>
 *   <li>{@code NEED_BIND}：展示 maskedIdentifier 引导用户输入完整凭证 + OTP，
 *       调用 {@code POST /auth/social/bind/otp} 完成绑定后再次获取 Token。</li>
 * </ul>
 */
@Data
@AllArgsConstructor
public class SocialLoginResponse {

    /** 状态标识：SUCCESS | NEED_BIND */
    private String status;

    // ── SUCCESS 字段（status=SUCCESS 时有值） ─────────────────────────────────
    private String accessToken;
    private Long   expiresIn;
    private Long   userId;

    // ── NEED_BIND 字段（status=NEED_BIND 时有值） ─────────────────────────────
    /** 一次性绑定票据（10 分钟有效），传给 /auth/social/bind/otp */
    private String bindTicket;
    /** 脱敏展示用标识符（如 138****5678），仅用于 UI 提示，不作为业务入参 */
    private String maskedIdentifier;

    public static SocialLoginResponse success(LoginResponse login) {
        return new SocialLoginResponse(
                "SUCCESS", login.getAccessToken(), login.getExpiresIn(), login.getUserId(),
                null, null);
    }

    public static SocialLoginResponse needBind(String bindTicket, String maskedIdentifier) {
        return new SocialLoginResponse(
                "NEED_BIND", null, null, null,
                bindTicket, maskedIdentifier);
    }
}