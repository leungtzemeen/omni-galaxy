package com.omnigalaxy.platform.auth.component;

/**
 * 凭证信任级别：声明辅助凭证（CredentialHint）的来源域，决定账号合并策略。
 *
 * <ul>
 *   <li>{@link #OWN}：本系统自有验证（如 OTP），信任链完整，可直接作为账号归并依据。</li>
 *   <li>{@link #EXTERNAL}：第三方平台声明（如微信小程序解密手机号），信任链不完整，
 *       不可替代本系统 OTP 证明，冲突时必须引导用户主动完成二次验证。</li>
 * </ul>
 */
public enum TrustLevel {
    OWN,
    EXTERNAL
}