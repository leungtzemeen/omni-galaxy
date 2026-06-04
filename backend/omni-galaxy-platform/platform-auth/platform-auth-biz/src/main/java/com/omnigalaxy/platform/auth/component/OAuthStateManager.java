package com.omnigalaxy.platform.auth.component;

import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.platform.auth.api.result.AuthResultCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * OAuth state 参数管理器：负责 state 的生成、存储与一次性原子消费，防御 OAuth CSRF 攻击。
 *
 * <h3>两种 state 类型及其防御边界</h3>
 *
 * <p><b>登录态 state（未认证用户发起扫码登录）</b>
 * <br>存储格式：{@code L{provider}}
 * <br>只验证 state 是否合法 + provider 是否匹配，无 userId 绑定。
 *
 * <p><b>绑定态 state（已认证用户发起社交账号绑定）</b>
 * <br>存储格式：{@code B{provider}{userId}}
 * <br>额外将 state 与发起请求的 {@code userId} 绑定，验证时强校验 userId 一致性：
 * <ul>
 *   <li>攻击者拿到受害者登录态，伪造 OAuth 回调 URL → 回调中 state 的 userId 与攻击者的 userId 不符 → 硬拒</li>
 *   <li>攻击者自己完成 OAuth，把回调链接发给受害者点击 → state 绑定的是攻击者 userId，
 *       而受害者的 JWT 中是受害者 userId → 不匹配 → 硬拒</li>
 * </ul>
 *
 * <h3>原子消费</h3>
 * <p>所有 {@code validate*} 方法均使用 {@code getAndDelete} 原子操作消费 state，
 * 同一 state 不可被重放，彻底防止 state 复用攻击。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthStateManager {

    private static final String   STATE_PREFIX    = "oauth:state:";
    private static final Duration STATE_TTL       = Duration.ofMinutes(5);
    private static final String   DELIMITER       = "";
    private static final String   LOGIN_TAG       = "L";
    private static final String   BIND_TAG        = "B";

    private final StringRedisTemplate redisTemplate;

    // =========================================================================
    // 登录态 state（未认证）
    // =========================================================================

    /**
     * 生成登录态 state，前端发起 OAuth 扫码前调用。
     *
     * @param provider 渠道标识（大写），如 {@code WECHAT}
     * @return 不透明随机 state 字符串（UUID 无连字符）
     */
    public String generateLoginState(String provider) {
        String state = randomState();
        String payload = LOGIN_TAG + DELIMITER + provider.toUpperCase();
        redisTemplate.opsForValue().set(STATE_PREFIX + state, payload, STATE_TTL);
        log.debug(">>>> [Auth-OAuth] 生成登录态 state provider: {}", provider);
        return state;
    }

    /**
     * 校验并原子消费登录态 state（防 CSRF）。
     *
     * @throws BizException {@link AuthResultCodeEnum#INVALID_OAUTH_STATE} state 过期、不存在或 provider 不符
     */
    public void validateAndConsumeLoginState(String provider, String state) {
        String stored = redisTemplate.opsForValue().getAndDelete(STATE_PREFIX + state);
        String expected = LOGIN_TAG + DELIMITER + provider.toUpperCase();
        if (stored == null || !stored.equals(expected)) {
            log.warn(">>>> [Auth-OAuth] 登录态 state 校验失败 provider: {} state: {}", provider, state);
            throw new BizException(AuthResultCodeEnum.INVALID_OAUTH_STATE);
        }
    }

    // =========================================================================
    // 绑定态 state（已认证，携带 userId 防 OAuth 劫持绑定攻击）
    // =========================================================================

    /**
     * 生成绑定态 state，已登录用户发起社交账号绑定时调用。
     *
     * <p>state 将与当前用户的 {@code userId} 绑定，OAuth 回调时强制校验一致性，
     * 彻底防范攻击者将自己的社交账号劫持绑定到受害者账号（OAuth 劫持绑定 / Binding CSRF）。
     *
     * @param provider      渠道标识（大写），如 {@code WECHAT}
     * @param currentUserId 发起绑定请求的已认证用户 ID（来自网关透传的 JWT 解析结果）
     * @return 不透明随机 state 字符串
     */
    public String generateBindState(String provider, Long currentUserId) {
        String state = randomState();
        String payload = BIND_TAG + DELIMITER + provider.toUpperCase() + DELIMITER + currentUserId;
        redisTemplate.opsForValue().set(STATE_PREFIX + state, payload, STATE_TTL);
        log.debug(">>>> [Auth-OAuth] 生成绑定态 state provider: {} userId: {}", provider, currentUserId);
        return state;
    }

    /**
     * 校验并原子消费绑定态 state，返回绑定时登记的 userId。
     *
     * <p>由于 OAuth 回调是浏览器重定向（不携带 JWT），userId 从 state 中提取，
     * 本方法同时扮演 CSRF 校验 + 身份恢复两个角色。
     *
     * @param provider 渠道标识（大写）
     * @param state    回调中的 state 参数
     * @return 绑定发起时的 userId
     * @throws BizException {@link AuthResultCodeEnum#INVALID_OAUTH_STATE} state 无效或 provider 不符
     */
    public Long validateAndConsumeBindState(String provider, String state) {
        String stored = redisTemplate.opsForValue().getAndDelete(STATE_PREFIX + state);
        if (stored == null) {
            log.warn(">>>> [Auth-OAuth] 绑定态 state 不存在或已过期 provider: {} state: {}", provider, state);
            throw new BizException(AuthResultCodeEnum.INVALID_OAUTH_STATE);
        }
        String[] parts = stored.split(DELIMITER, 3);
        if (parts.length != 3 || !BIND_TAG.equals(parts[0]) || !provider.toUpperCase().equals(parts[1])) {
            log.warn(">>>> [Auth-OAuth] 绑定态 state 格式或 provider 不符 stored: {} expected: {}", stored, provider);
            throw new BizException(AuthResultCodeEnum.INVALID_OAUTH_STATE);
        }
        try {
            return Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            log.error(">>>> [核心底座] 绑定态 state userId 解析失败 stored: {}", stored);
            throw new BizException(AuthResultCodeEnum.INVALID_OAUTH_STATE);
        }
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    private static String randomState() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}