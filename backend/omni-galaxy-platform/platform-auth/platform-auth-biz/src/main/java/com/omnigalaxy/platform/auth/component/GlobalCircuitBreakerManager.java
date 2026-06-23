package com.omnigalaxy.platform.auth.component;

import com.omnigalaxy.platform.auth.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 全域熔断管理器：当用户触发高风险凭证变更（修改密码、风控强制下线）时，
 * 向 Redis 写入熔断时间戳，使该用户在此时间点之前签发的全部 Token 失效。
 *
 * <p>Key 设计：{@code auth:reset:{userId}} = epoch millis（字符串）。
 * 网关 {@code JwtVerificationFilter} 在校验每个请求时并行读取此 Key，
 * 若 Token {@code iat} 早于该时间戳则判定为已撤销（401）。
 *
 * <p>TTL 策略：等于 {@link JwtProperties#getExpireMs()}。
 * 最老的合法 Token 最多存活 expireMs 毫秒，Key 生存期覆盖这一窗口后自然清除，
 * 无需手动删除，亦不产生 Redis 常驻垃圾。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalCircuitBreakerManager {

    private static final String RESET_PREFIX = "auth:reset:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties       jwtProperties;

    /**
     * 为指定用户触发全端 Token 熔断。调用后该用户所有存量 Token 将在下次请求时被网关拦截。
     *
     * @param userId 被熔断的用户 ID
     */
    public void revokeAllTokens(Long userId) {
        String key        = RESET_PREFIX + userId;
        String nowMillis  = String.valueOf(System.currentTimeMillis());
        Duration ttl      = Duration.ofMillis(jwtProperties.getExpireMs());

        redisTemplate.opsForValue().set(key, nowMillis, ttl);
        log.info("<<<< [Auth] 全域熔断已触发，存量 Token 全部失效 userId: {} ttlMs: {}", userId, ttl.toMillis());
    }
}
