package com.omnigalaxy.platform.auth.service.impl;

import com.omnigalaxy.common.captcha.enums.OtpScene;
import com.omnigalaxy.common.captcha.manager.OtpManager;
import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.platform.auth.api.dto.LoginResponse;
import com.omnigalaxy.platform.auth.api.dto.SocialLoginResponse;
import com.omnigalaxy.platform.auth.api.result.AuthResultCodeEnum;
import com.omnigalaxy.platform.auth.component.AccountLifecycleManager;
import com.omnigalaxy.platform.auth.component.OAuthStateManager;
import com.omnigalaxy.platform.auth.component.TokenIssuer;
import com.omnigalaxy.platform.auth.component.SocialLoginResult;
import com.omnigalaxy.platform.auth.domain.UserCredential;
import com.omnigalaxy.platform.auth.dto.BindSocialRequest;
import com.omnigalaxy.platform.auth.dto.MiniProgramLoginRequest;
import com.omnigalaxy.platform.auth.oauth.SocialIdentity;
import com.omnigalaxy.platform.auth.oauth.SocialLoginStrategy;
import com.omnigalaxy.platform.auth.service.SocialAuthService;
import com.omnigalaxy.platform.auth.service.UserCredentialService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 社交登录服务实现：编排 state 管理、code-exchange、账户生命周期与令牌签发全链路。
 *
 * <p>本类保持单一职责——协议层编排。各子任务分别委托给专职组件：
 * <ul>
 *   <li>CSRF 防御 → {@link OAuthStateManager}</li>
 *   <li>code 换取身份 → {@link SocialLoginStrategy}（按 identityType 路由）</li>
 *   <li>账户发现/合并/注册 → {@link AccountLifecycleManager}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAuthServiceImpl implements SocialAuthService {

    private final List<SocialLoginStrategy> strategyList;
    private final OAuthStateManager         oauthStateManager;
    private final AccountLifecycleManager   accountLifecycleManager;
    private final UserCredentialService     credentialService;
    private final OtpManager               otpManager;
    private final TokenIssuer              tokenIssuer;

    /** 按 identityType（大写）索引策略，启动时构建一次，运行期只读 */
    private Map<String, SocialLoginStrategy> strategies;

    @PostConstruct
    public void init() {
        strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        s -> s.identityType().toUpperCase(),
                        Function.identity()
                ));
        log.info(">>>> [Auth-Social] 已注册社交登录渠道: {}", strategies.keySet());
    }

    // =========================================================================
    // 登录态
    // =========================================================================

    @Override
    public String generateLoginState(String provider) {
        requireStrategy(provider);
        return oauthStateManager.generateLoginState(provider);
    }

    @Override
    public SocialLoginResponse handleWebCallback(String provider, String code, String state) {
        log.info(">>>> [Auth-Social] 网页 OAuth 回调 provider: {}", provider);

        // 1. CSRF 校验：原子消费 state，防重放
        oauthStateManager.validateAndConsumeLoginState(provider, state);

        // 2. code-exchange：换取平台身份
        SocialLoginStrategy strategy = requireStrategy(provider);
        SocialIdentity identity = strategy.exchangeWebCode(code);

        // 3. 账户发现/注册（无 hint，网页端仅有 unionId）
        SocialLoginResult result = accountLifecycleManager.findOrMergeAccountWithHints(
                strategy.identityType(), identity.openId(), List.of());

        return toSocialResponse(result, provider);
    }

    @Override
    public SocialLoginResponse handleMiniProgramLogin(MiniProgramLoginRequest request) {
        log.info(">>>> [Auth-Social] 小程序登录 provider: {} hasPhone: {}",
                request.getProvider(), request.getEncryptedPhone() != null);

        // 小程序无需 state（不走浏览器重定向，天然无 CSRF）
        SocialLoginStrategy strategy = requireStrategy(request.getProvider());
        SocialIdentity identity = strategy.exchangeMiniProgramCode(
                request.getCode(), request.getEncryptedPhone(), request.getIv());

        // hints 由策略层直接标注好信任级别，服务层无需感知渠道细节，直接透传
        SocialLoginResult result = accountLifecycleManager.findOrMergeAccountWithHints(
                strategy.identityType(), identity.openId(), identity.hints());

        return toSocialResponse(result, request.getProvider());
    }

    // =========================================================================
    // 绑定态（已认证用户）
    // =========================================================================

    @Override
    public String generateBindState(String provider, Long currentUserId) {
        requireStrategy(provider);
        // 将 userId 绑入 state，OAuth 回调时用于防 CSRF + 身份恢复
        return oauthStateManager.generateBindState(provider, currentUserId);
    }

    @Override
    public void handleBindCallback(String provider, String code, String state) {
        log.info(">>>> [Auth-Social] 绑定 OAuth 回调 provider: {}", provider);

        // 1. CSRF 校验 + 提取发起绑定时的 userId（回调不携带 JWT，从 state 恢复身份）
        Long currentUserId = oauthStateManager.validateAndConsumeBindState(provider, state);

        // 2. code-exchange
        SocialLoginStrategy strategy = requireStrategy(provider);
        SocialIdentity identity = strategy.exchangeWebCode(code);
        String identityType = strategy.identityType();

        log.info(">>>> [Auth-Social] 绑定回调 identityType: {} userId: {}", identityType, currentUserId);

        // 3. 检查该社交凭证是否已被绑定
        UserCredential existing = credentialService.findByIdentity(identityType, identity.openId());
        if (existing != null) {
            if (existing.getUserId().equals(currentUserId)) {
                log.info("<<<< [Auth-Social] 社交账号已绑定（幂等） identityType: {} userId: {}", identityType, currentUserId);
                return; // 幂等成功
            }
            throw new BizException(AuthResultCodeEnum.SOCIAL_ACCOUNT_BOUND_TO_ANOTHER);
        }

        // 4. 追加凭证到当前用户，零建档
        try {
            credentialService.saveCredential(identityType, identity.openId(), currentUserId);
            log.info("<<<< [Auth-Social] 社交账号绑定成功 identityType: {} userId: {}", identityType, currentUserId);
        } catch (DataIntegrityViolationException ex) {
            // 极端并发：re-read 检查是否已被绑定到同一账号（幂等自愈）
            UserCredential reCred = credentialService.findByIdentity(identityType, identity.openId());
            if (reCred != null && reCred.getUserId().equals(currentUserId)) return;
            throw new BizException(AuthResultCodeEnum.SOCIAL_ACCOUNT_BOUND_TO_ANOTHER);
        }
    }

    @Override
    public LoginResponse bindByOtp(BindSocialRequest request) {
        String identifier   = request.getIdentifier();
        String identityType = request.getIdentityType();

        log.info(">>>> [Auth-Social] OTP 绑定票据消费 identityType: {}", identityType);

        // 0. 预检：ticket 的 conflictType/conflictIdentifier 必须与入参严格一致（GET 不消费票据）
        //    阻断攻击者用错误 identityType + 自己的合法 OTP 绕过语义校验，同时保护 ticket 不被提前销毁
        accountLifecycleManager.preValidateBindTicket(request.getBindTicket(), identityType, identifier);

        // 1. OTP 校验：预检通过后，identityType/identifier 已确认与票据一致，语义完整
        if (!otpManager.verify(OtpScene.LOGIN, identityType, identifier, request.getOtpCode())) {
            throw new BizException(AuthResultCodeEnum.OTP_INVALID);
        }

        // 2. 原子消费票据，执行凭证追加
        Long userId = accountLifecycleManager.consumeBindTicket(request.getBindTicket(), identifier);

        log.info("<<<< [Auth-Social] OTP 绑定成功，签发 Token userId: {}", userId);
        return tokenIssuer.issue(userId);
    }

    // =========================================================================
    // 私有工具
    // =========================================================================

    private SocialLoginStrategy requireStrategy(String provider) {
        SocialLoginStrategy strategy = strategies.get(provider.toUpperCase());
        if (strategy == null) {
            throw new BizException(AuthResultCodeEnum.OTP_SCENE_UNSUPPORTED);
        }
        return strategy;
    }

    /**
     * 将 {@link SocialLoginResult} 转换为 Controller 层响应体。
     * NEED_BIND 时禁止签发 Token，直接透传 bindTicket。
     */
    private SocialLoginResponse toSocialResponse(SocialLoginResult result, String provider) {
        if (!result.canIssueToken()) {
            log.info("<<<< [Auth-Social] 信任域冲突，返回 NEED_BIND provider: {}", provider);
            return SocialLoginResponse.needBind(result.bindTicket(), result.maskedIdentifier());
        }
        log.info("<<<< [Auth-Social] 社交登录成功 provider: {} userId: {}", provider, result.userId());
        return SocialLoginResponse.success(tokenIssuer.issue(result.userId()));
    }

}