package com.omnigalaxy.platform.auth.service.impl;

import com.omnigalaxy.common.captcha.enums.OtpScene;
import com.omnigalaxy.common.captcha.manager.OtpManager;
import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.common.core.result.Result;
import com.omnigalaxy.common.core.result.ResultCodeEnum;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.omnigalaxy.platform.auth.api.dto.LoginResponse;
import com.omnigalaxy.platform.auth.api.result.AuthResultCodeEnum;
import com.omnigalaxy.platform.auth.domain.UserCredential;
import com.omnigalaxy.platform.auth.dto.OtpLoginRequest;
import com.omnigalaxy.platform.auth.dto.PasswordLoginRequest;
import com.omnigalaxy.platform.auth.service.AuthService;
import com.omnigalaxy.platform.auth.service.UserCredentialService;
import com.omnigalaxy.platform.auth.util.JwtUtils;
import com.omnigalaxy.platform.user.api.client.UserProfileClient;
import com.omnigalaxy.platform.user.api.dto.UserProfileCreateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    public LoginResponse loginByPassword(PasswordLoginRequest request) {
        throw new BizException(AuthResultCodeEnum.PASSWORD_LOGIN_NOT_IMPL);
    }

    // -------------------------------------------------------------------------

    /**
     * 新用户注册链路：Redis 锁 → Double-check → RPC 创建档案（锁外无事务）→ 短写事务写凭证。
     * DataIntegrityViolationException 兜底处理极端并发场景（主键/唯一键冲突）。
     */
    private Long registerNewUser(String identityType, String identifier) {
        String lockKey = REG_LOCK_PREFIX + identityType + ":" + identifier;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", REG_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ResultCodeEnum.TOO_MANY_REQUESTS, 1L);
        }
        try {
            // Double-check：获锁后确认锁等待期间是否已由其他线程完成注册
            UserCredential cred = credentialService.findByIdentity(identityType, identifier);
            if (cred != null) {
                return cred.getUserId();
            }

            // RPC 调用：位于任何事务边界之外，防止远端延迟卡死本地连接池
            UserProfileCreateDTO dto = new UserProfileCreateDTO();
            dto.setIdempotencyKey(buildIdempotencyKey(identityType, identifier));
            Result<Long> result = userProfileClient.createProfile(dto);
            Long userId = result.getData();

            // 短写事务：仅包裹一条 INSERT，毫秒级持有 DB 连接
            credentialService.saveCredential(identityType, identifier, userId);
            return userId;

        } catch (DataIntegrityViolationException ex) {
            // 极端并发兜底：唯一约束冲突说明另一线程已写入，re-read 复用结果
            log.warn(">>>> [Auth] 凭证唯一约束触发（极端并发），re-read 复用结果 identityType: {}", identityType);
            UserCredential cred = credentialService.findByIdentity(identityType, identifier);
            return cred.getUserId();
        } finally {
            redisTemplate.delete(lockKey);
        }
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