package com.omnigalaxy.platform.auth.oauth;

import com.omnigalaxy.platform.auth.component.CredentialHint;

import java.util.List;

/**
 * 第三方平台换回的用户身份摘要，由 {@link SocialLoginStrategy} 实现层构造并填充。
 *
 * <p>{@code openId} 是主凭证 identifier，建议优先使用 {@code unionId}（跨应用唯一）而非 openId
 * （仅应用内唯一），确保同平台多应用下同一用户不撕裂。
 *
 * <p>{@code hints} 由策略层直接标注辅助凭证及其信任级别，无需上层推断：
 * 微信小程序解密手机号属于 {@link com.omnigalaxy.platform.auth.component.TrustLevel#EXTERNAL}，
 * 策略在构造 SocialIdentity 时直接写入，服务层照单全收传给 AccountLifecycleManager，
 * 保持各层职责清晰。
 *
 * @param openId   平台唯一用户标识（微信 unionId / 支付宝 userId 等）
 * @param nickname 昵称，可为 null
 * @param avatarUrl 头像 URL，可为 null
 * @param hints    策略层携带的辅助凭证列表（如小程序解密手机号），无时传 {@link List#of()}
 */
public record SocialIdentity(
        String openId,
        String nickname,
        String avatarUrl,
        List<CredentialHint> hints
) {
    /**
     * 无辅助 hint 的便捷构造器，适用于 PC 网页扫码等仅携带主凭证的场景。
     */
    public SocialIdentity(String openId, String nickname, String avatarUrl) {
        this(openId, nickname, avatarUrl, List.of());
    }
}