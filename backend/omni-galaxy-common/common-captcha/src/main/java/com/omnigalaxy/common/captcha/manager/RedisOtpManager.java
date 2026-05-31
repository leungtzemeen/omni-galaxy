package com.omnigalaxy.common.captcha.manager;

import com.omnigalaxy.common.captcha.enums.OtpScene;
import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.common.core.result.ResultCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的 OTP 管理器默认实现。
 *
 * <p>Key 设计：
 * <ul>
 *   <li>OTP key：{@code otp:{scene}:{identityType}:{identifier}}，TTL 5 分钟</li>
 *   <li>冷却 key：{@code otp:cooldown:{identityType}:{identifier}}，TTL 1 分钟</li>
 * </ul>
 * 冷却 key 不含 scene，确保跨场景切换无法绕过频率限制。
 */
@Slf4j
@RequiredArgsConstructor
public class RedisOtpManager implements OtpManager {

    private static final long OTP_TTL_SECONDS      = 300L;
    private static final long COOLDOWN_TTL_SECONDS  = 60L;
    private static final String OTP_PREFIX          = "otp:";
    private static final String COOLDOWN_PREFIX     = "otp:cooldown:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public String generateAndStore(OtpScene scene, String identityType, String identifier) {
        if (isOnCooldown(identityType, identifier)) {
            throw new BizException(ResultCodeEnum.TOO_MANY_REQUESTS, COOLDOWN_TTL_SECONDS / 60);
        }
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        redisTemplate.opsForValue().set(otpKey(scene, identityType, identifier), code, OTP_TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(cooldownKey(identityType, identifier), "1", COOLDOWN_TTL_SECONDS, TimeUnit.SECONDS);
        log.info(">>>> [OtpManager] 验证码已生成 scene: {} identityType: {} identifier: {}",
                scene, identityType, mask(identifier));
        return code;
    }

    @Override
    public boolean verify(OtpScene scene, String identityType, String identifier, String code) {
        String key    = otpKey(scene, identityType, identifier);
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null || !stored.equals(code)) {
            log.warn(">>>> [OtpManager] 验证码校验失败（无效或已过期）identityType: {} identifier: {}",
                    identityType, mask(identifier));
            return false;
        }
        redisTemplate.delete(key);
        return true;
    }

    @Override
    public boolean isOnCooldown(String identityType, String identifier) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey(identityType, identifier)));
    }

    // -------------------------------------------------------------------------

    private String otpKey(OtpScene scene, String identityType, String identifier) {
        return OTP_PREFIX + scene.name().toLowerCase() + ":" + identityType.toLowerCase() + ":" + identifier;
    }

    private String cooldownKey(String identityType, String identifier) {
        return COOLDOWN_PREFIX + identityType.toLowerCase() + ":" + identifier;
    }

    private String mask(String s) {
        if (s == null || s.length() <= 4) return "****";
        return s.substring(0, 3) + "****" + s.substring(s.length() - 2);
    }
}
