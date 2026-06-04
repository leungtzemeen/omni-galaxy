package com.omnigalaxy.platform.auth.service.impl;

import com.omnigalaxy.common.captcha.enums.OtpScene;
import com.omnigalaxy.common.captcha.manager.OtpManager;
import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.platform.auth.api.dto.LoginResponse;
import com.omnigalaxy.platform.auth.api.result.AuthResultCodeEnum;
import com.omnigalaxy.platform.auth.component.AccountLifecycleManager;
import com.omnigalaxy.platform.auth.component.LoginRateLimiter;
import com.omnigalaxy.platform.auth.domain.UserCredential;
import com.omnigalaxy.platform.auth.dto.OtpLoginRequest;
import com.omnigalaxy.platform.auth.dto.PasswordLoginRequest;
import com.omnigalaxy.platform.auth.dto.PasswordRegisterRequest;
import com.omnigalaxy.platform.auth.service.AuthService;
import com.omnigalaxy.platform.auth.service.UserCredentialService;
import com.omnigalaxy.platform.auth.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

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

    private final OtpManager              otpManager;
    private final JwtUtils                jwtUtils;
    private final UserCredentialService   credentialService;
    private final AccountLifecycleManager accountLifecycleManager;
    private final PasswordEncoder         passwordEncoder;
    private final LoginRateLimiter        rateLimiter;

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
            return signToken(cred.getUserId());
        }

        // 慢速路径：新用户 → 委托 Manager 完成账户发现/合并/注册（含分布式锁 + 自愈）
        Long userId = accountLifecycleManager.findOrMergeAccount(identityType, identifier);
        log.info("<<<< [Auth] 新用户 OTP 登注成功 identityType: {} userId: {}", identityType, userId);
        return signToken(userId);
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
        return signToken(userId);
    }

    @Override
    public LoginResponse loginByPassword(PasswordLoginRequest request) {
        String identifier = request.getIdentifier();

        log.info(">>>> [Auth] 密码登录请求 identifier: {}", mask(identifier));

        // 防爆破：命中封禁直接短路，不查库（避免被爆破放大为 DB 压力）
        rateLimiter.checkAndThrowIfBanned(identifier);

        UserCredential cred = credentialService.findByIdentity("PASSWORD", identifier);
        // 账号不存在与密码错误统一返回相同错误，防用户枚举攻击
        if (cred == null || !passwordEncoder.matches(request.getPassword(), cred.getCredential())) {
            rateLimiter.recordFailure(identifier);
            throw new BizException(AuthResultCodeEnum.PASSWORD_WRONG);
        }

        // 密码正确后再检查禁用状态，避免在 BCrypt 校验前泄露账号存在性
        if (cred.getStatus() == 1) {
            throw new BizException(AuthResultCodeEnum.ACCOUNT_DISABLED);
        }

        rateLimiter.clearFailures(identifier);
        log.info("<<<< [Auth] 密码登录成功 userId: {}", cred.getUserId());
        return signToken(cred.getUserId());
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    private LoginResponse signToken(Long userId) {
        return new LoginResponse(
                jwtUtils.generateToken(userId, List.of("ROLE_USER")),
                jwtUtils.getExpireSeconds(),
                userId
        );
    }

    private String mask(String s) {
        if (s == null || s.length() <= 4) return "****";
        return s.substring(0, 3) + "****" + s.substring(s.length() - 2);
    }
}