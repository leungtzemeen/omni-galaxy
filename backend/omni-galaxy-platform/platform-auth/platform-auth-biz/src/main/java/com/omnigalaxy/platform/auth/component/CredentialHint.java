package com.omnigalaxy.platform.auth.component;

/**
 * 辅助凭证断言：第三方渠道在主凭证之外额外返回的身份信息及其信任来源。
 *
 * <p>典型使用场景：微信小程序登录时，后端解密可同时获得 unionId（主凭证）和手机号（辅助 hint）。
 * 通过 {@link TrustLevel} 标注信任来源，{@link AccountLifecycleManager} 据此决定是否自动合并。
 *
 * @param identityType 凭证类型（PHONE / EMAIL）
 * @param identifier   凭证标识符（E.164 手机号或邮箱）
 * @param trustLevel   信任来源，{@link TrustLevel#EXTERNAL} 表示第三方声明，冲突时禁止自动合并
 */
public record CredentialHint(String identityType, String identifier, TrustLevel trustLevel) {}