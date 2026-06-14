package com.omnigalaxy.platform.auth.component;

import com.omnigalaxy.common.security.jwt.JwtClaimsResolver;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 登出黑名单管理：将已登出的 Token 写入 Redis，TTL 严格等于该 Token 的剩余生命周期，
 * 到期后自动从 Redis 清除，不产生常驻内存浪费。
 *
 * <p>Key 设计：{@code auth:token:blacklist:{jti}}，与 platform-gateway 的
 * JwtVerificationFilter 共享同一套 Redis 实例与 Key 命名约定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklistManager {

    private static final String BLACKLIST_PREFIX = "auth:token:blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final JwtClaimsResolver jwtClaimsResolver;

    /**
     * 拉黑指定 Token。登出是幂等操作：Token 本身已失效（签名错误/已过期/缺失 jti）时
     * 直接放行不报错，避免向客户端泄露 Token 校验细节。
     */
    public void revoke(String token) {
        JwtClaimsResolver.ResolvedToken resolved;
        try {
            resolved = jwtClaimsResolver.resolve(token);
        } catch (JwtException e) {
            log.warn(">>>> [Auth] 登出请求携带的 Token 签名无效或已过期，无需拉黑（处理策略：直接放行）: {}", e.getMessage());
            return;
        }

        String jti = resolved.jti();
        if (jti == null) {
            log.warn(">>>> [Auth] Token 缺失 jti（升级前签发的旧版本 Token），无法精确拉黑 userId: {}", resolved.userId());
            return;
        }

        // 剩余生命周期统一按 epoch millis 计算，与 JwtVerificationFilter 的判定单位保持一致
        long ttlMillis = resolved.expiration().getTime() - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            log.info("<<<< [Auth] Token 已过期，跳过黑名单写入 jti: {}", jti);
            return;
        }

        redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", Duration.ofMillis(ttlMillis));
        log.info("<<<< [Auth] 登出成功，Token 已加入黑名单 userId: {} jti: {} ttlMillis: {}", resolved.userId(), jti, ttlMillis);
    }
}