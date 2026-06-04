package com.omnigalaxy.platform.auth.oauth;

/**
 * 第三方社交登录策略：封装各平台 OAuth code-exchange 协议差异，对上层屏蔽渠道细节。
 *
 * <h3>扩展说明</h3>
 * <p>新增渠道（GitHub、Google 等）只需实现本接口并注册为 Spring Bean，
 * {@link com.omnigalaxy.platform.auth.service.impl.SocialAuthServiceImpl} 会自动发现并路由，无需修改现有代码（OCP）。
 *
 * <h3>identityType 与 user_credential 的关系</h3>
 * <p>{@link #identityType()} 直接对应 {@code user_credential.identity_type} 列值（大写），
 * 决定凭证在多凭证模型中的行类型。
 */
public interface SocialLoginStrategy {

    /**
     * 凭证类型标识，全局唯一，对应 {@code user_credential.identity_type}。
     * 示例：{@code "WECHAT"}、{@code "ALIPAY"}
     */
    String identityType();

    /**
     * PC 网页 OAuth code 换取平台身份（Authorization Code Flow）。
     *
     * @param code 微信/支付宝等平台回调携带的一次性 code
     * @return 换回的平台用户身份，openId 作为凭证 identifier
     */
    SocialIdentity exchangeWebCode(String code);

    /**
     * 小程序 code 换取平台身份，并可选解密手机号 hint。
     *
     * <p>小程序使用 {@code wx.login()} 获取 code，与网页 OAuth 的 code-exchange 协议完全不同：
     * 通过 {@code jscode2session} 拿到 {@code session_key}，再用 session_key + iv 解密 encryptedPhone。
     *
     * @param code           {@code wx.login()} 返回的 code
     * @param encryptedPhone {@code wx.getUserPhone()} 返回的加密手机号，可为 null（用户未授权）
     * @param iv             AES-128-CBC 解密向量，与 encryptedPhone 配对使用
     * @return 换回的平台用户身份，附带解密后的手机号（phone 字段）
     */
    default SocialIdentity exchangeMiniProgramCode(String code, String encryptedPhone, String iv) {
        throw new UnsupportedOperationException(identityType() + " 不支持小程序登录");
    }
}