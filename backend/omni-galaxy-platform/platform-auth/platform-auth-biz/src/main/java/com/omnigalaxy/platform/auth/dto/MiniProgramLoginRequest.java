package com.omnigalaxy.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 小程序登录请求。
 *
 * <p>{@code code} 来自 {@code wx.login()}，必填。
 * {@code encryptedPhone} + {@code iv} 来自 {@code wx.getUserPhone()}，用户授权后才有值，选填。
 * 后端据此决定是否携带 PHONE hint 参与多凭证登注。
 */
@Data
public class MiniProgramLoginRequest {

    /** 渠道标识，大写，如 {@code WECHAT} */
    @NotBlank(message = "{validation.social.provider.notBlank}")
    private String provider;

    /** {@code wx.login()} 返回的临时登录凭证 */
    @NotBlank(message = "{validation.social.code.notBlank}")
    private String code;

    /** {@code wx.getUserPhone()} 返回的加密手机号，用户未授权时为 null */
    private String encryptedPhone;

    /** AES-128-CBC 解密向量，与 encryptedPhone 配套使用，encryptedPhone 为 null 时忽略 */
    private String iv;
}