package com.omnigalaxy.platform.auth.oauth.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.platform.auth.api.result.AuthResultCodeEnum;
import com.omnigalaxy.platform.auth.component.CredentialHint;
import com.omnigalaxy.platform.auth.component.TrustLevel;
import com.omnigalaxy.platform.auth.oauth.SocialIdentity;
import com.omnigalaxy.platform.auth.oauth.SocialLoginStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 微信渠道登录策略：同时支持 PC 网页 OAuth 扫码和小程序 jscode2session + AES 手机号解密。
 *
 * <h3>身份标识策略</h3>
 * <p>统一优先使用 {@code unionId}（微信开放平台跨应用唯一标识），降级使用 {@code openId}
 * （仅当前应用内唯一）。确保公众号、小程序、网页端的同一自然人在本系统内收敛到同一 userId。
 *
 * <h3>双重熔断</h3>
 * <ul>
 *   <li><b>物理熔断</b>：网络超时、DNS 解析失败等 I/O 异常 → {@code catch} 捕获 →
 *       转换为 {@link AuthResultCodeEnum#OAUTH_CODE_EXCHANGE_FAILED}</li>
 *   <li><b>业务熔断</b>：微信 HTTP 200 陷阱——响应体中 {@code errcode≠0} →
 *       显式抛出 {@link AuthResultCodeEnum#OAUTH_CODE_EXCHANGE_FAILED}，
 *       拒绝微信脏错误渗透上层 Service</li>
 * </ul>
 *
 * <h3>启用前置条件</h3>
 * <ol>
 *   <li>微信开放平台完成账号注册、应用绑定，获取 appId / appSecret。</li>
 *   <li>将本服务回调域名加入微信开放平台白名单。</li>
 *   <li>在 {@code application.yml} 中填写真实的 {@code wechat.web.*} 和 {@code wechat.mini.*} 属性。</li>
 * </ol>
 */
@Slf4j
@Component
public class WechatStrategy implements SocialLoginStrategy {

    private static final String WEB_TOKEN_URL =
            "https://api.weixin.qq.com/sns/oauth2/access_token" +
            "?appid={appId}&secret={secret}&code={code}&grant_type=authorization_code";

    private static final String MINI_SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session" +
            "?appid={appId}&secret={secret}&js_code={code}&grant_type=authorization_code";

    // 研发期使用 PLACEHOLDER，生产前必须替换为真实密钥
    @Value("${wechat.web.app-id:PLACEHOLDER}")     private String webAppId;
    @Value("${wechat.web.app-secret:PLACEHOLDER}")  private String webAppSecret;
    @Value("${wechat.mini.app-id:PLACEHOLDER}")     private String miniAppId;
    @Value("${wechat.mini.app-secret:PLACEHOLDER}") private String miniAppSecret;

    private final RestClient   restClient;
    private final ObjectMapper objectMapper;

    /**
     * 基于 JDK 原生 {@link HttpClient} 构建 RestClient，天然对 Java 21 虚拟线程友好。
     * connectTimeout=5s 防 DNS 卡死；readTimeout=10s 防微信慢响应长时间 hang 住线程。
     */
    public WechatStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String identityType() {
        return "WECHAT";
    }

    // =========================================================================
    // 网页 OAuth
    // =========================================================================

    @Override
    public SocialIdentity exchangeWebCode(String code) {
        log.info(">>>> [Auth-Wechat] 网页 OAuth code 换取 unionId");
        try {
            // 取 String body：调用点在类内部，private record 反序列化无 Java 模块封装风险
            String body = restClient.get()
                    .uri(WEB_TOKEN_URL, Map.of("appId", webAppId, "secret", webAppSecret, "code", code))
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(body)) {
                log.warn(">>>> [Auth-Wechat] 微信 OAuth 返回空响应体");
                throw new BizException(AuthResultCodeEnum.OAUTH_CODE_EXCHANGE_FAILED);
            }

            WechatTokenResponse resp = objectMapper.readValue(body, WechatTokenResponse.class);

            // 业务熔断：HTTP 200 ≠ 成功，必须检查 errcode
            if (resp.isError()) {
                log.warn(">>>> [Auth-Wechat] 微信 OAuth code 换取业务失败 errcode: {} errmsg: {}",
                        resp.errcode(), resp.errmsg());
                throw new BizException(AuthResultCodeEnum.OAUTH_CODE_EXCHANGE_FAILED);
            }

            String identifier = StringUtils.hasText(resp.unionid()) ? resp.unionid() : resp.openid();
            log.info("<<<< [Auth-Wechat] 网页 OAuth 换取成功 hasUnionId: {}", StringUtils.hasText(resp.unionid()));
            // 网页扫码无手机号，使用无 hints 的三参数便捷构造器
            return new SocialIdentity(identifier, null, null);

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 物理熔断：I/O 超时、DNS 失败、JSON 解析异常等统一收口
            log.error(">>>> [核心底座] 微信网页 OAuth code 换取失败", e);
            throw new BizException(AuthResultCodeEnum.OAUTH_CODE_EXCHANGE_FAILED);
        }
    }

    // =========================================================================
    // 小程序 jscode2session + AES 手机号解密
    // =========================================================================

    @Override
    public SocialIdentity exchangeMiniProgramCode(String code, String encryptedPhone, String iv) {
        log.info(">>>> [Auth-Wechat] 小程序 jscode2session hasPhone: {}", encryptedPhone != null);
        try {
            String body = restClient.get()
                    .uri(MINI_SESSION_URL, Map.of("appId", miniAppId, "secret", miniAppSecret, "code", code))
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(body)) {
                log.warn(">>>> [Auth-Wechat] 小程序 jscode2session 返回空响应体");
                throw new BizException(AuthResultCodeEnum.OAUTH_CODE_EXCHANGE_FAILED);
            }

            WechatSessionResponse resp = objectMapper.readValue(body, WechatSessionResponse.class);

            // 业务熔断
            if (resp.isError()) {
                log.warn(">>>> [Auth-Wechat] 小程序 jscode2session 业务失败 errcode: {} errmsg: {}",
                        resp.errcode(), resp.errmsg());
                throw new BizException(AuthResultCodeEnum.OAUTH_CODE_EXCHANGE_FAILED);
            }

            String identifier = StringUtils.hasText(resp.unionid()) ? resp.unionid() : resp.openid();

            // 解密手机号并由本策略直接标注 EXTERNAL 信任级别，降级不阻断主登录流程
            List<CredentialHint> hints = buildPhoneHints(resp.sessionKey(), encryptedPhone, iv);

            log.info("<<<< [Auth-Wechat] 小程序换取成功 hasUnionId: {} phoneHints: {}",
                    StringUtils.hasText(resp.unionid()), hints.size());
            return new SocialIdentity(identifier, null, null, hints);

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error(">>>> [核心底座] 微信小程序 jscode2session 失败", e);
            throw new BizException(AuthResultCodeEnum.OAUTH_CODE_EXCHANGE_FAILED);
        }
    }

    // =========================================================================
    // 私有：AES-128-CBC 手机号解密
    // =========================================================================

    /**
     * 解密微信手机号并封装为 {@link TrustLevel#EXTERNAL} hint。
     *
     * <p>sessionKey 5 分钟内有效，过期后解密必然失败。此时静默降级（返回空列表），
     * 不抛异常——用户仍能通过 unionId 完成登录，只是暂时无法享受 PHONE 自动关联。
     *
     * <p>强制使用 {@code phoneNumber}（含 {@code +} 前缀的 E.164 格式），
     * 拒绝 {@code purePhoneNumber}（无国家码，跨区域有二义性）。
     */
    private List<CredentialHint> buildPhoneHints(String sessionKey, String encryptedPhone, String iv) {
        if (!StringUtils.hasText(encryptedPhone) || !StringUtils.hasText(iv)) {
            return List.of();
        }
        try {
            String decryptedJson = decryptAesCbc(sessionKey, encryptedPhone, iv);
            WechatPhoneData phoneData = objectMapper.readValue(decryptedJson, WechatPhoneData.class);

            if (!StringUtils.hasText(phoneData.phoneNumber())) {
                log.warn(">>>> [Auth-Wechat] 解密 JSON 中 phoneNumber 字段为空，降级跳过 hint");
                return List.of();
            }
            log.info(">>>> [Auth-Wechat] 手机号解密成功，封装 EXTERNAL hint");
            return List.of(new CredentialHint("PHONE", phoneData.phoneNumber(), TrustLevel.EXTERNAL));

        } catch (Exception e) {
            // sessionKey 过期是最常见原因，静默降级保障主流程畅通
            log.warn(">>>> [Auth-Wechat] 手机号解密降级（sessionKey 可能已过期）: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * AES-128-CBC 解密，使用 JDK 原生 {@code javax.crypto.Cipher}，零额外三方依赖。
     *
     * @param base64Key     Base64 编码的 sessionKey（解码后 16 字节）
     * @param base64EncData Base64 编码的密文
     * @param base64Iv      Base64 编码的初始化向量（解码后 16 字节）
     * @return 解密后的明文字符串（UTF-8）
     */
    private String decryptAesCbc(String base64Key, String base64EncData, String base64Iv)
            throws GeneralSecurityException {
        byte[] keyBytes  = Base64.getDecoder().decode(base64Key);
        byte[] dataBytes = Base64.getDecoder().decode(base64EncData);
        byte[] ivBytes   = Base64.getDecoder().decode(base64Iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(keyBytes, "AES"),
                new IvParameterSpec(ivBytes));
        return new String(cipher.doFinal(dataBytes), StandardCharsets.UTF_8);
    }

    // =========================================================================
    // 私有：微信 API 响应防腐模型
    // 微信蛇形命名（access_token / session_key / errcode 等）锁死在此，绝不渗透上层
    // =========================================================================

    private record WechatTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("openid")       String openid,
            @JsonProperty("unionid")      String unionid,
            @JsonProperty("errcode")      Integer errcode,
            @JsonProperty("errmsg")       String errmsg
    ) {
        /** 微信 HTTP 200 陷阱：errcode 存在且不为 0 时视为业务失败。 */
        boolean isError() { return errcode != null && errcode != 0; }
    }

    private record WechatSessionResponse(
            @JsonProperty("session_key") String sessionKey,
            @JsonProperty("openid")      String openid,
            @JsonProperty("unionid")     String unionid,
            @JsonProperty("errcode")     Integer errcode,
            @JsonProperty("errmsg")      String errmsg
    ) {
        boolean isError() { return errcode != null && errcode != 0; }
    }

    private record WechatPhoneData(
            @JsonProperty("phoneNumber")     String phoneNumber,     // E.164（含 + 前缀），凭证表标准
            @JsonProperty("purePhoneNumber") String purePhoneNumber, // 仅备参考，不作为凭证 identifier
            @JsonProperty("countryCode")     String countryCode
    ) {}
}
