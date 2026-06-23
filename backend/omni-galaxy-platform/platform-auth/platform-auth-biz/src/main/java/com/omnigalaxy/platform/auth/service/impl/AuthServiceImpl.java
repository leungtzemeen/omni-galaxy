package com.omnigalaxy.platform.auth.service.impl;

import com.omnigalaxy.common.captcha.enums.OtpScene;
import com.omnigalaxy.common.captcha.manager.HumanVerificationManager;
import com.omnigalaxy.common.captcha.manager.OtpManager;
import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.platform.auth.api.dto.LoginResponse;
import com.omnigalaxy.platform.auth.api.result.AuthResultCodeEnum;
import com.omnigalaxy.platform.auth.component.AccountLifecycleManager;
import com.omnigalaxy.platform.auth.component.GlobalCircuitBreakerManager;
import com.omnigalaxy.platform.auth.component.LoginRateLimiter;
import com.omnigalaxy.platform.auth.component.TokenBlacklistManager;
import com.omnigalaxy.platform.auth.component.TokenIssuer;
import com.omnigalaxy.platform.auth.domain.UserCredential;
import com.omnigalaxy.platform.auth.dto.ChangePasswordRequest;
import com.omnigalaxy.platform.auth.dto.OtpLoginRequest;
import com.omnigalaxy.platform.auth.dto.PasswordLoginRequest;
import com.omnigalaxy.platform.auth.dto.PasswordRegisterRequest;
import com.omnigalaxy.platform.auth.exception.CaptchaChallengeException;
import com.omnigalaxy.platform.auth.service.AuthService;
import com.omnigalaxy.platform.auth.service.UserCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现：负责协议层校验（OTP 验签、BCrypt 比对、速率限制）与令牌签发。
 *
 * <p>账户生命周期的核心逻辑（分布式锁、碰撞检测、凭证写入、跨渠道合并）
 * 统一委托给 {@link AccountLifecycleManager}，本类不感知具体的账户创建细节。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final OtpManager                otpManager;
    private final TokenIssuer               tokenIssuer;
    private final UserCredentialService     credentialService;
    private final AccountLifecycleManager   accountLifecycleManager;
    private final PasswordEncoder           passwordEncoder;
    private final LoginRateLimiter          rateLimiter;
    private final HumanVerificationManager  humanVerificationManager;
    private final TokenBlacklistManager     tokenBlacklistManager;
    private final GlobalCircuitBreakerManager circuitBreakerManager;

    @Override
    public LoginResponse loginByOtp(OtpLoginRequest request) {
        String identifier   = request.getIdentifier();
        String identityType = request.getIdentityType();

        log.info(">>>> [Auth] OTP 登录请求 identityType: {} identifier: {}", identityType, mask(identifier));

        if (!otpManager.verify(OtpScene.LOGIN, identityType, identifier, request.getOtpCode())) {
            throw new BizException(AuthResultCodeEnum.OTP_INVALID);
        }

        // 快速路径：凭证已存在 → 直接签发，不进入账户生命周期管理（覆盖 ~99% 的老用户登录）
        UserCredential cred = credentialService.findByIdentity(identityType, identifier);
        if (cred != null) {
            log.info("<<<< [Auth] 老用户 OTP 登录成功 userId: {}", cred.getUserId());
            return tokenIssuer.issue(cred.getUserId());
        }

        // 慢速路径：新用户 → 委托 Manager 完成账户发现/合并/注册（含分布式锁 + 自愈）
        Long userId = accountLifecycleManager.findOrMergeAccount(identityType, identifier);
        log.info("<<<< [Auth] 新用户 OTP 登注成功 identityType: {} userId: {}", identityType, userId);
        return tokenIssuer.issue(userId);
    }

    @Override
    public LoginResponse registerByPassword(PasswordRegisterRequest request) {
        String identifier   = request.getIdentifier();
        String identityType = request.getIdentityType();

        log.info(">>>> [Auth] 账密注册请求 identityType: {} identifier: {}", identityType, mask(identifier));

        // OTP 校验确认 identifier 归属权（注册场景），验通后委托 Manager 执行账户创建（Strategy B）
        if (!otpManager.verify(OtpScene.REGISTER, identityType, identifier, request.getOtpCode())) {
            throw new BizException(AuthResultCodeEnum.OTP_INVALID);
        }

        Long userId = accountLifecycleManager.registerNewAccountWithCredential(
                identityType, identifier, passwordEncoder.encode(request.getPassword()));

        log.info("<<<< [Auth] 账密注册成功 identityType: {} userId: {}", identityType, userId);
        return tokenIssuer.issue(userId);
    }

    @Override
    public LoginResponse loginByPassword(PasswordLoginRequest request) {
        String identifier = request.getIdentifier();

        log.info(">>>> [Auth] 密码登录请求 identifier: {}", mask(identifier));

        // 防爆破：命中封禁直接短路，不查库（避免被爆破放大为 DB 压力）
        rateLimiter.checkAndThrowIfBanned(identifier);
        // 命中验证码连错冷却也直接短路，避免冷却期内继续消耗验证码挑战
        rateLimiter.checkCaptchaCooldown(identifier);

        // 失败计数达到软挑战阈值：要求先通过图形验证码，验证码失败不计入密码失败计数
        if (rateLimiter.needsChallenge(identifier)) {
            verifyHumanChallenge(identifier, request.getChallengeId(), request.getChallengeAnswer());
        }

        UserCredential cred = credentialService.findByIdentity("PASSWORD", identifier);
        // 账号不存在与密码错误统一返回相同错误，防用户枚举攻击
        if (cred == null || !passwordEncoder.matches(request.getPassword(), cred.getCredential())) {
            rateLimiter.recordFailure(identifier);
            if (rateLimiter.needsChallenge(identifier)) {
                // 仍处于软挑战区间：响应体携带新挑战，前端无脑用最新值重试，杜绝过期 challengeId 死循环
                throw new CaptchaChallengeException(AuthResultCodeEnum.PASSWORD_WRONG, humanVerificationManager.generateChallenge());
            }
            throw new BizException(AuthResultCodeEnum.PASSWORD_WRONG);
        }

        // 密码正确后再检查禁用状态，避免在 BCrypt 校验前泄露账号存在性
        if (cred.getStatus() == 1) {
            throw new BizException(AuthResultCodeEnum.ACCOUNT_DISABLED);
        }

        rateLimiter.clearFailures(identifier);
        log.info("<<<< [Auth] 密码登录成功 userId: {}", cred.getUserId());
        return tokenIssuer.issue(cred.getUserId());
    }

    @Override
    public void logout(String token) {
        log.info(">>>> [Auth] 登出请求 context: token={}****", token.substring(0, Math.min(8, token.length())));
        tokenBlacklistManager.revoke(token);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        String identifier   = request.getIdentifier();
        String identityType = request.getIdentityType();

        log.info(">>>> [Auth] 修改密码请求 userId: {} identityType: {} identifier: {}", userId, identityType, mask(identifier));

        // 1. OTP 验证：确认请求方对该手机/邮箱拥有控制权
        if (!otpManager.verify(OtpScene.RESET_PWD, identityType, identifier, request.getOtpCode())) {
            throw new BizException(AuthResultCodeEnum.OTP_INVALID);
        }

        // 2. 归属校验：确认该手机/邮箱凭证确实属于当前登录用户，防止用他人 OTP 串改账号
        UserCredential identifierCred = credentialService.findByIdentity(identityType, identifier);
        if (identifierCred == null || !userId.equals(identifierCred.getUserId())) {
            log.warn(">>>> [Auth] 修改密码归属校验失败，identifier 不属于当前账号（处理策略：403）userId: {} identifier: {}",
                    userId, mask(identifier));
            throw new BizException(AuthResultCodeEnum.IDENTIFIER_NOT_BELONG);
        }

        // 3. 确认该账号已有 PASSWORD 凭证，无则说明是纯 OTP 账号，应走账密注册绑定流程
        UserCredential passwordCred = credentialService.findByUserIdAndType(userId, "PASSWORD");
        if (passwordCred == null) {
            throw new BizException(AuthResultCodeEnum.PASSWORD_NOT_SET);
        }

        // 4. 更新密码哈希
        credentialService.updatePassword(userId, passwordEncoder.encode(request.getNewPassword()));

        // 5. 全域熔断：使该用户所有存量 Token 立即失效，防止旧 Token 继续使用旧密码的会话
        circuitBreakerManager.revokeAllTokens(userId);

        log.info("<<<< [Auth] 修改密码成功，存量 Token 已全部熔断 userId: {}", userId);
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    /**
     * 校验软挑战图形验证码。verify 内部使用 Redis GETDEL 原子销毁，无论结果如何挑战立即失效，
     * 因此失败时必须随响应携带一张全新挑战图，否则前端下一次提交会因 challengeId 已销毁而必然失败。
     */
    private void verifyHumanChallenge(String identifier, String challengeId, String challengeAnswer) {
        if (challengeId == null || challengeAnswer == null) {
            throw new CaptchaChallengeException(AuthResultCodeEnum.CAPTCHA_REQUIRED, humanVerificationManager.generateChallenge());
        }
        if (!humanVerificationManager.verify(challengeId, challengeAnswer)) {
            boolean cooldownTriggered = rateLimiter.recordCaptchaFailure(identifier);
            AuthResultCodeEnum code = cooldownTriggered ? AuthResultCodeEnum.CAPTCHA_FAIL_TOO_MANY : AuthResultCodeEnum.CAPTCHA_WRONG;
            throw new CaptchaChallengeException(code, humanVerificationManager.generateChallenge());
        }
        rateLimiter.clearCaptchaFailure(identifier);
    }

    private String mask(String s) {
        if (s == null || s.length() <= 4) return "****";
        return s.substring(0, 3) + "****" + s.substring(s.length() - 2);
    }
}