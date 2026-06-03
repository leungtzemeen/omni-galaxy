package com.omnigalaxy.platform.auth.service.impl;

import com.omnigalaxy.common.captcha.enums.OtpScene;
import com.omnigalaxy.common.captcha.manager.OtpManager;
import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.common.core.result.Result;
import com.omnigalaxy.common.core.result.ResultCodeEnum;
import com.omnigalaxy.platform.auth.api.dto.LoginResponse;
import com.omnigalaxy.platform.auth.api.result.AuthResultCodeEnum;
import com.omnigalaxy.platform.auth.component.LoginRateLimiter;
import com.omnigalaxy.platform.auth.domain.UserCredential;
import com.omnigalaxy.platform.auth.dto.OtpLoginRequest;
import com.omnigalaxy.platform.auth.dto.PasswordLoginRequest;
import com.omnigalaxy.platform.auth.dto.PasswordRegisterRequest;
import com.omnigalaxy.platform.auth.service.AuthService;
import com.omnigalaxy.platform.auth.service.UserCredentialService;
import com.omnigalaxy.platform.auth.util.JwtUtils;
import com.omnigalaxy.platform.user.api.client.UserProfileClient;
import com.omnigalaxy.platform.user.api.dto.UserProfileCreateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String   REG_LOCK_PREFIX = "reg:lock:";
    private static final Duration REG_LOCK_TTL    = Duration.ofSeconds(10);

    private final OtpManager            otpManager;
    private final JwtUtils              jwtUtils;
    private final UserCredentialService credentialService;
    private final UserProfileClient     userProfileClient;
    private final StringRedisTemplate   redisTemplate;
    private final PasswordEncoder       passwordEncoder;
    private final LoginRateLimiter      rateLimiter;

    @Override
    public LoginResponse loginByOtp(OtpLoginRequest request) {
        String identifier   = request.getIdentifier();
        String identityType = request.getIdentityType();

        log.info(">>>> [Auth] OTP 登录请求 identityType: {} identifier: {}", identityType, mask(identifier));

        boolean valid = otpManager.verify(OtpScene.LOGIN, identityType, identifier, request.getOtpCode());
        if (!valid) {
            throw new BizException(AuthResultCodeEnum.OTP_INVALID);
        }

        // 快速路径：已有凭证 → 直接签发
        UserCredential cred = credentialService.findByIdentity(identityType, identifier);
        if (cred != null) {
            log.info("<<<< [Auth] 老用户登录成功 identityType: {} userId: {}", identityType, cred.getUserId());
            return signToken(cred.getUserId());
        }

        // 慢速路径：新用户一键登注
        Long userId = registerNewUser(identityType, identifier);
        log.info("<<<< [Auth] 新用户注册并登录成功 identityType: {} userId: {}", identityType, userId);
        return signToken(userId);
    }

    @Override
    public LoginResponse registerByPassword(PasswordRegisterRequest request) {
        String identifier   = request.getIdentifier();
        String identityType = request.getIdentityType();

        log.info(">>>> [Auth] 账密注册请求 identityType: {} identifier: {}", identityType, mask(identifier));

        boolean valid = otpManager.verify(OtpScene.REGISTER, identityType, identifier, request.getOtpCode());
        if (!valid) {
            throw new BizException(AuthResultCodeEnum.OTP_INVALID);
        }

        // 乐观路径：锁外快速碰撞检测，减少锁内压力
        checkIdentifierCollision(identifier);

        String lockKey = REG_LOCK_PREFIX + identifier;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", REG_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ResultCodeEnum.TOO_MANY_REQUESTS, 1L);
        }
        try {
            // Double-check：获锁后再次确认
            checkIdentifierCollision(identifier);

            UserProfileCreateDTO dto = new UserProfileCreateDTO();
            dto.setIdempotencyKey(buildIdempotencyKey("PASSWORD", identifier));
            Result<Long> result = userProfileClient.createProfile(dto);
            Long userId = result.getData();

            credentialService.saveCredential("PASSWORD", identifier, userId, passwordEncoder.encode(request.getPassword()));
            log.info("<<<< [Auth] 账密注册成功 identityType: {} userId: {}", identityType, userId);
            return signToken(userId);

        } catch (DataIntegrityViolationException ex) {
            // 极端并发：唯一约束触发，说明另一线程已抢先写入
            log.warn(">>>> [Auth] 账密注册唯一约束冲突（极端并发） identityType: {}", identityType);
            throw new BizException(AuthResultCodeEnum.IDENTIFIER_ALREADY_REGISTERED);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Override
    public LoginResponse loginByPassword(PasswordLoginRequest request) {
        String identifier = request.getIdentifier();

        log.info(">>>> [Auth] 密码登录请求 identifier: {}", mask(identifier));

        // 防爆破入口拦截，命中直接短路，不查库
        rateLimiter.checkAndThrowIfBanned(identifier);

        UserCredential cred = credentialService.findByIdentity("PASSWORD", identifier);
        // 账号不存在与密码错误统一返回相同错误，防止用户枚举攻击
        if (cred == null || !passwordEncoder.matches(request.getPassword(), cred.getCredential())) {
            rateLimiter.recordFailure(identifier);
            throw new BizException(AuthResultCodeEnum.PASSWORD_WRONG);
        }

        // 密码正确后再检查禁用状态，避免在校验前泄露账号存在性
        if (cred.getStatus() == 1) {
            throw new BizException(AuthResultCodeEnum.ACCOUNT_DISABLED);
        }

        rateLimiter.clearFailures(identifier);
        log.info("<<<< [Auth] 密码登录成功 userId: {}", cred.getUserId());
        return signToken(cred.getUserId());
    }

    // -------------------------------------------------------------------------

    /**
     * 新用户 OTP 注册链路：Redis 锁 → Double-check → 跨类型老用户识别 → RPC 创建档案 → 短写事务写凭证。
     *
     * 跨类型合并逻辑：若同一 identifier 已以其他 identityType（如 PASSWORD）注册，
     * OTP 验证已证明归属权，直接追加新凭证行并复用既有 userId，不重新建档。
     * DataIntegrityViolationException 兜底处理极端并发场景（唯一键冲突）。
     */
    private Long registerNewUser(String identityType, String identifier) {
        String lockKey = REG_LOCK_PREFIX + identifier;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", REG_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ResultCodeEnum.TOO_MANY_REQUESTS, 1L);
        }
        try {
            // 同类型 double-check（锁等待期间可能已由其他线程完成）
            UserCredential cred = credentialService.findByIdentity(identityType, identifier);
            if (cred != null) {
                return cred.getUserId();
            }

            // 跨类型老用户识别：同 identifier 已以其他方式注册 → 追加凭证，复用 userId，禁止重新建档
            UserCredential crossCred = credentialService.findAnyByIdentifier(identifier);
            if (crossCred != null) {
                log.info(">>>> [Auth] OTP 识别到跨类型老用户，追加凭证复用账号 identityType: {} userId: {}",
                        identityType, crossCred.getUserId());
                credentialService.saveCredential(identityType, identifier, crossCred.getUserId());
                return crossCred.getUserId();
            }

            // 真正的全新用户：RPC 建档（位于事务边界之外）→ 短写事务写凭证
            UserProfileCreateDTO dto = new UserProfileCreateDTO();
            dto.setIdempotencyKey(buildIdempotencyKey(identityType, identifier));
            Result<Long> result = userProfileClient.createProfile(dto);
            Long userId = result.getData();

            credentialService.saveCredential(identityType, identifier, userId);
            return userId;

        } catch (DataIntegrityViolationException ex) {
            log.warn(">>>> [Auth] 凭证唯一约束触发（极端并发），re-read 复用结果 identityType: {}", identityType);
            UserCredential cred = credentialService.findByIdentity(identityType, identifier);
            return cred.getUserId();
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 账密注册碰撞检测：跨 identityType 查询同一 identifier 是否已被注册，
     * 并根据已有凭证类型给出精确的分流提示。
     */
    private void checkIdentifierCollision(String identifier) {
        UserCredential existing = credentialService.findAnyByIdentifier(identifier);
        if (existing == null) return;
        if ("PASSWORD".equals(existing.getIdentityType())) {
            throw new BizException(AuthResultCodeEnum.IDENTIFIER_ALREADY_REGISTERED);
        }
        throw new BizException(AuthResultCodeEnum.IDENTIFIER_OTP_REGISTERED);
    }

    private LoginResponse signToken(Long userId) {
        return new LoginResponse(
                jwtUtils.generateToken(userId, List.of("ROLE_USER")),
                jwtUtils.getExpireSeconds(),
                userId
        );
    }

    /** SHA-256(identityType:identifier) 十六进制字符串，作为 createProfile 的不透明幂等键 */
    private static String buildIdempotencyKey(String identityType, String identifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((identityType + ":" + identifier).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 在 JVM 中始终可用
        }
    }

    private String mask(String s) {
        if (s == null || s.length() <= 4) return "****";
        return s.substring(0, 3) + "****" + s.substring(s.length() - 2);
    }
}