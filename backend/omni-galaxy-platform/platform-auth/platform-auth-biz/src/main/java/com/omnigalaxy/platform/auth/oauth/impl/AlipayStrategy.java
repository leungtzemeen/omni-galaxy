package com.omnigalaxy.platform.auth.oauth.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.platform.auth.api.result.AuthResultCodeEnum;
import com.omnigalaxy.platform.auth.component.CredentialHint;
import com.omnigalaxy.platform.auth.component.TrustLevel;
import com.omnigalaxy.platform.auth.oauth.SocialIdentity;
import com.omnigalaxy.platform.auth.oauth.SocialLoginStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 支付宝渠道登录策略：支持 PC 网页 OAuth 扫码与小程序授权两种接入方式。
 *
 * <h3>身份标识策略</h3>
 * <p>使用支付宝 {@code user_id}，该值在同一开发者主体下跨应用全局唯一，
 * 与微信 unionId 语义对等，作为本系统凭证表中 ALIPAY 行类型的 identifier。
 *
 * <h3>签名机制</h3>
 * <p>支付宝所有接口调用必须附带 RSA2（SHA256WithRSA）签名，使用 JDK 原生
 * {@link java.security.Signature} 实现，零额外三方依赖。签名规范：
 * 所有参数按键名字典序排列 → 拼接为 {@code k=v&k=v} → SHA256WithRSA 签名 → Base64 编码。
 *
 * <h3>手机号获取（小程序）</h3>
 * <p>小程序端调用 {@code my.getPhoneNumber()} 得到密文 response，
 * 服务端透传至 {@code alipay.user.getphone} 接口由支付宝服务端完成解密，
 * 无需本地 AES 操作（区别于微信 sessionKey + AES-CBC 方案）。
 *
 * <h3>双重熔断</h3>
 * <ul>
 *   <li><b>物理熔断</b>：网络超时、DNS 解析等 I/O 异常统一转换为 {@code OAUTH_CODE_EXCHANGE_FAILED}</li>
 *   <li><b>业务熔断</b>：支付宝 HTTP 200 时响应 code 可能不为 "10000" → 显式拒绝脏错误渗透上层</li>
 * </ul>
 *
 * <h3>启用前置条件</h3>
 * <ol>
 *   <li>支付宝开放平台注册应用，获取 appId 并生成 RSA2 密钥对。</li>
 *   <li>将公钥上传至支付宝开放平台，将私钥填入 {@code application.yml}。</li>
 *   <li>小程序授权手机号需在平台申请 "获取会员手机号" 权限。</li>
 * </ol>
 */
@Slf4j
@Component
public class AlipayStrategy implements SocialLoginStrategy {

    private static final String GATEWAY_URL    = "https://openapi.alipay.com/gateway.do";
    private static final String METHOD_TOKEN   = "alipay.system.oauth.token";
    private static final String METHOD_PHONE   = "alipay.user.getphone";
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 研发期使用 PLACEHOLDER，生产前必须替换为真实密钥（开发者控制台生成）
    @Value("${alipay.app-id:PLACEHOLDER}")
    private String appId;

    /** PKCS#8 格式 RSA2 私钥，Base64 编码，不含 Header/Footer 行 */
    @Value("${alipay.private-key:PLACEHOLDER}")
    private String rsaPrivateKey;

    private final RestClient   restClient;
    private final ObjectMapper objectMapper;

    /**
     * 基于 JDK 原生 {@link HttpClient} 构建 RestClient，与 Java 21 虚拟线程天然兼容。
     * connectTimeout=5s 防 DNS 卡死；readTimeout=10s 防支付宝慢响应 hang 住线程。
     */
    public AlipayStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String identityType() {
        return "ALIPAY";
    }

    // =========================================================================
    // 网页 OAuth
    // =========================================================================

    @Override
    public SocialIdentity exchangeWebCode(String code) {
        log.info(">>>> [Auth-Alipay] 网页 OAuth code 换取 userId");
        try {
            String userId = fetchUserId(code);
            log.info("<<<< [Auth-Alipay] 网页 OAuth 换取成功");
            return new SocialIdentity(userId, null, null);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error(">>>> [核心底座] 支付宝网页 OAuth code 换取失败", e);
            throw new BizException(AuthResultCodeEnum.OAUTH_CODE_EXCHANGE_FAILED);
        }
    }

    // =========================================================================
    // 小程序授权
    // =========================================================================

    @Override
    public SocialIdentity exchangeMiniProgramCode(String code, String encryptedPhone, String iv) {
        // iv 对支付宝无语义：手机号由 alipay.user.getphone 服务端解密，不需要 AES iv
        log.info(">>>> [Auth-Alipay] 小程序 OAuth code 换取 userId hasPhone: {}", encryptedPhone != null);
        try {
            String userId = fetchUserId(code);
            List<CredentialHint> hints = fetchPhoneHints(encryptedPhone);

            log.info("<<<< [Auth-Alipay] 小程序换取成功 phoneHints: {}", hints.size());
            return new SocialIdentity(userId, null, null, hints);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error(">>>> [核心底座] 支付宝小程序 OAuth code 换取失败", e);
            throw new BizException(AuthResultCodeEnum.OAUTH_CODE_EXCHANGE_FAILED);
        }
    }

    // =========================================================================
    // 私有：API 调用编排
    // =========================================================================

    private String fetchUserId(String code) throws Exception {
        TreeMap<String, String> params = buildBaseParams(METHOD_TOKEN);
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("sign", sign(params));

        String body = post(params);
        JsonNode resp = objectMapper.readTree(body)
                .path("alipay_system_oauth_token_response");

        if (isOauthTokenError(resp)) {
            log.warn(">>>> [Auth-Alipay] OAuth token 业务失败 sub_code: {}",
                    resp.path("sub_code").asText("unknown"));
            throw new BizException(AuthResultCodeEnum.OAUTH_CODE_EXCHANGE_FAILED);
        }

        String userId = resp.path("user_id").asText();
        if (!StringUtils.hasText(userId)) {
            log.warn(">>>> [Auth-Alipay] OAuth token 响应中 user_id 字段缺失");
            throw new BizException(AuthResultCodeEnum.OAUTH_CODE_EXCHANGE_FAILED);
        }
        return userId;
    }

    /**
     * 调用 {@code alipay.user.getphone} 获取手机号，解密由支付宝服务端完成。
     *
     * <p>小程序调用 {@code my.getPhoneNumber()} 后，前端获得的 {@code response} 密文
     * 直接透传此处，无需本地 AES 解密（区别于微信 sessionKey 方案）。
     *
     * <p>手机号获取失败时静默降级返回空列表，主登录流程不受阻断。
     */
    private List<CredentialHint> fetchPhoneHints(String encryptedResponse) {
        if (!StringUtils.hasText(encryptedResponse)) {
            return List.of();
        }
        try {
            TreeMap<String, String> params = buildBaseParams(METHOD_PHONE);
            // biz_content 为内嵌 JSON 字符串，encryptedResponse 来自 my.getPhoneNumber() 的 response 字段
            params.put("biz_content", "{\"response\":\"" + encryptedResponse + "\"}");
            params.put("sign", sign(params));

            String body = post(params);
            JsonNode resp = objectMapper.readTree(body)
                    .path("alipay_user_getphone_response");

            if (isAlipayError(resp)) {
                log.warn(">>>> [Auth-Alipay] 手机号获取业务失败，降级 sub_code: {}",
                        resp.path("sub_code").asText("unknown"));
                return List.of();
            }

            String mobile = resp.path("mobile").asText("");
            if (!StringUtils.hasText(mobile)) {
                log.warn(">>>> [Auth-Alipay] 手机号字段为空，降级跳过 hint");
                return List.of();
            }

            log.info(">>>> [Auth-Alipay] 手机号获取成功，封装 EXTERNAL hint");
            return List.of(new CredentialHint("PHONE", mobile, TrustLevel.EXTERNAL));

        } catch (Exception e) {
            // 权限未开通、密文过期等均静默降级，手机号 hint 仅属辅助关联
            log.warn(">>>> [Auth-Alipay] 手机号获取降级: {}", e.getMessage());
            return List.of();
        }
    }

    // =========================================================================
    // 私有：RSA2 签名与请求构建
    // =========================================================================

    /**
     * 构建支付宝公共参数集（{@link TreeMap} 保证键名字典序，为签名排序奠基）。
     */
    private TreeMap<String, String> buildBaseParams(String method) {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("app_id",    appId);
        params.put("method",    method);
        params.put("charset",   "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FMT));
        params.put("version",   "1.0");
        return params;
    }

    /**
     * RSA2（SHA256WithRSA）签名。
     *
     * <p>规范：所有非 sign 的非空参数按键名字典升序排列 → 拼接为 {@code k=v&k=v}
     * → SHA256WithRSA 签名 → Base64 编码。
     * {@link TreeMap} 已保证字典序，stream 仅做 sign 过滤与格式拼接。
     */
    private String sign(TreeMap<String, String> params) throws GeneralSecurityException {
        String content = params.entrySet().stream()
                .filter(e -> !"sign".equals(e.getKey()) && StringUtils.hasText(e.getValue()))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

        byte[] keyBytes = Base64.getDecoder().decode(rsaPrivateKey);
        PrivateKey pk = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        Signature sig = Signature.getInstance("SHA256WithRSA");
        sig.initSign(pk);
        sig.update(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    /** 以 POST application/x-www-form-urlencoded 调用支付宝网关，返回原始 JSON 响应体。 */
    private String post(TreeMap<String, String> params) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);
        String body = restClient.post()
                .uri(GATEWAY_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        if (!StringUtils.hasText(body)) {
            log.warn(">>>> [Auth-Alipay] 网关返回空响应体");
            throw new BizException(AuthResultCodeEnum.OAUTH_CODE_EXCHANGE_FAILED);
        }
        return body;
    }

    /**
     * 专用于 {@code alipay.system.oauth.token} 的错误判断。
     *
     * <p>该 API 成功时响应体<b>无 code 字段</b>，直接携带 access_token / user_id；
     * 失败时才出现 code（如 "40002"）+ sub_code + msg。
     * 因此"code 字段存在且不为 10000"才是错误信号，节点本身缺失也视为异常。
     */
    private boolean isOauthTokenError(JsonNode respNode) {
        if (respNode.isMissingNode()) return true;
        String code = respNode.path("code").asText("");
        // 成功响应 code 缺失（空串）；失败响应 code 为具体错误码
        return StringUtils.hasText(code) && !"10000".equals(code);
    }

    /**
     * 适用于有 code 字段标志成功的 API（如 {@code alipay.user.getphone}）。
     * 成功响应固定携带 {@code "code": "10000"}，节点缺失或其他 code 均视为业务失败。
     */
    private boolean isAlipayError(JsonNode respNode) {
        return respNode.isMissingNode() || !"10000".equals(respNode.path("code").asText());
    }
}