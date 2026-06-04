package com.omnigalaxy.platform.auth.oauth.impl;

import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.platform.auth.api.result.AuthResultCodeEnum;
import com.omnigalaxy.platform.auth.component.CredentialHint;
import com.omnigalaxy.platform.auth.component.TrustLevel;
import com.omnigalaxy.platform.auth.oauth.SocialIdentity;
import com.omnigalaxy.platform.auth.oauth.SocialLoginStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 微信渠道登录策略：同时支持 PC 网页扫码（OAuth 2.0）和小程序登录（jscode2session + AES 解密）。
 *
 * <p>身份标识统一使用 {@code unionId}（跨应用唯一），而非 openId（仅应用内唯一），
 * 确保微信公众号、小程序、网页端的同一用户在本系统内归属同一 userId。
 *
 * <h3>启用前置条件</h3>
 * <ol>
 *   <li>在微信开放平台完成账号注册及应用绑定，获取 appId / appSecret。</li>
 *   <li>在 {@code application.yml} 中配置 {@code wechat.web.*} 和 {@code wechat.mini.*} 属性。</li>
 *   <li>将本服务域名添加至微信开放平台的回调域白名单。</li>
 * </ol>
 */
@Slf4j
@Component
public class WechatStrategy implements SocialLoginStrategy {

    /** 网页 OAuth access_token 换取端点 */
    private static final String WEB_TOKEN_URL =
            "https://api.weixin.qq.com/sns/oauth2/access_token" +
            "?appid={appId}&secret={secret}&code={code}&grant_type=authorization_code";

    /** 小程序 code-to-session 端点 */
    private static final String MINI_SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session" +
            "?appid={appId}&secret={secret}&js_code={code}&grant_type=authorization_code";

    @Value("${wechat.web.app-id:PLACEHOLDER}") private String webAppId;
    @Value("${wechat.web.app-secret:PLACEHOLDER}") private String webAppSecret;
    @Value("${wechat.mini.app-id:PLACEHOLDER}") private String miniAppId;
    @Value("${wechat.mini.app-secret:PLACEHOLDER}") private String miniAppSecret;

    private final RestTemplate restTemplate;

    public WechatStrategy(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String identityType() {
        return "WECHAT";
    }

    /**
     * PC 网页 OAuth code 换取 unionId（无辅助 hint，网页扫码不返回手机号）。
     *
     * <p>调用微信 {@code /sns/oauth2/access_token} 换取 access_token + unionId。
     * 若开放平台未绑定或用户未关注公众号，unionId 可能为空，降级使用 openId。
     */
    @Override
    public SocialIdentity exchangeWebCode(String code) {
        log.info(">>>> [Auth-Wechat] 网页 OAuth code 换取 unionId");
        try {
            // TODO: 实现微信网页 OAuth HTTP 调用
            // 1. GET WEB_TOKEN_URL with {appId, secret, code}
            // 2. 解析响应中的 unionId（优先）或 openId（降级）
            // 3. 可选：调用 /sns/userinfo 获取昵称和头像
            // 4. 网页扫码无手机号 hint，使用无参 hints 的便捷构造器：
            //    return new SocialIdentity(identifier, nickname, avatarUrl);
            //
            // 参考实现：
            // Map<String, String> params = Map.of("appId", webAppId, "secret", webAppSecret, "code", code);
            // WechatTokenResponse resp = restTemplate.getForObject(WEB_TOKEN_URL, WechatTokenResponse.class, params);
            // if (resp == null || resp.getErrcode() != 0) throw new BizException(OAUTH_CODE_EXCHANGE_FAILED);
            // String identifier = StringUtils.hasText(resp.getUnionid()) ? resp.getUnionid() : resp.getOpenid();
            // return new SocialIdentity(identifier, resp.getNickname(), resp.getHeadimgurl());
            throw new UnsupportedOperationException("微信网页 OAuth 尚未配置，请填充 wechat.web 属性并实现 HTTP 调用");
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            log.error(">>>> [核心底座] 微信网页 OAuth code 换取失败", e);
            throw new BizException(AuthResultCodeEnum.OAUTH_CODE_EXCHANGE_FAILED);
        }
    }

    /**
     * 小程序 code 换取 unionId，并可选解密手机号作为 EXTERNAL hint 写入 SocialIdentity。
     *
     * <p>解密出的手机号由本策略直接标注为 {@link TrustLevel#EXTERNAL}（第三方声明，非本系统 OTP），
     * 装入 {@code hints} 字段随身份对象传递给上层，上层无需感知信任级别来源。
     */
    @Override
    public SocialIdentity exchangeMiniProgramCode(String code, String encryptedPhone, String iv) {
        log.info(">>>> [Auth-Wechat] 小程序 code 换取 unionId hasPhone: {}", encryptedPhone != null);
        try {
            // TODO: 实现微信小程序 jscode2session + 手机号解密
            // 1. GET MINI_SESSION_URL with {appId, secret, code}
            //    Map<String, String> params = Map.of("appId", miniAppId, "secret", miniAppSecret, "code", code);
            //    WechatSessionResponse resp = restTemplate.getForObject(MINI_SESSION_URL, WechatSessionResponse.class, params);
            //    if (resp == null || resp.getErrcode() != 0) throw new BizException(OAUTH_CODE_EXCHANGE_FAILED);
            //
            // 2. 获取 unionId（优先）/ openId（降级）
            //    String identifier = StringUtils.hasText(resp.getUnionid()) ? resp.getUnionid() : resp.getOpenid();
            //    String sessionKey = resp.getSessionKey();
            //
            // 3. 若 encryptedPhone != null && iv != null，AES-128-CBC 解密手机号：
            //    byte[] keyBytes  = Base64.getDecoder().decode(sessionKey);
            //    byte[] ivBytes   = Base64.getDecoder().decode(iv);
            //    byte[] encBytes  = Base64.getDecoder().decode(encryptedPhone);
            //    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            //    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(ivBytes));
            //    String json = new String(cipher.doFinal(encBytes), StandardCharsets.UTF_8);
            //    String phone = JsonPath.read(json, "$.phoneNumber");  // E.164 格式，如 +8613812345678
            //
            // 4. 策略层直接标注 phone 的信任级别（EXTERNAL），装入 hints 字段：
            //    List<CredentialHint> hints = StringUtils.hasText(phone)
            //        ? List.of(new CredentialHint("PHONE", phone, TrustLevel.EXTERNAL))
            //        : List.of();
            //    return new SocialIdentity(identifier, null, null, hints);
            throw new UnsupportedOperationException("微信小程序登录尚未配置，请填充 wechat.mini 属性并实现 jscode2session 调用");
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            log.error(">>>> [核心底座] 微信小程序 code 换取失败", e);
            throw new BizException(AuthResultCodeEnum.OAUTH_CODE_EXCHANGE_FAILED);
        }
    }
}