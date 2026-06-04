package com.omnigalaxy.platform.auth.component;

/**
 * 社交登录/注册状态机输出：由 {@link AccountLifecycleManager} 产出，调用方据此决定后续路由。
 *
 * <h3>状态路由规则</h3>
 * <ul>
 *   <li>{@link Status#LOGIN} / {@link Status#REGISTERED_FULL}：{@code userId} 有效，
 *       调用方可直接签发 JWT。</li>
 *   <li>{@link Status#NEED_BIND}：{@code userId} 为 {@code null}，
 *       <strong>禁止签发 JWT</strong>，必须向前端返回 {@code bindTicket} 引导 OTP 验证绑定，
 *       防止信任域不对称引发的越权登录。</li>
 * </ul>
 *
 * @param userId           登录/注册成功时的用户 ID；NEED_BIND 时为 null
 * @param status           当前状态机输出状态
 * @param bindTicket       NEED_BIND 时的临时绑定票据（10 分钟有效，一次性消费）；其余为 null
 * @param maskedIdentifier NEED_BIND 时用于前端展示的脱敏 identifier；其余为 null
 */
public record SocialLoginResult(
        Long userId,
        Status status,
        String bindTicket,
        String maskedIdentifier
) {

    public static SocialLoginResult login(Long userId) {
        return new SocialLoginResult(userId, Status.LOGIN, null, null);
    }

    public static SocialLoginResult registeredFull(Long userId) {
        return new SocialLoginResult(userId, Status.REGISTERED_FULL, null, null);
    }

    public static SocialLoginResult needBind(String ticket, String maskedIdentifier) {
        return new SocialLoginResult(null, Status.NEED_BIND, ticket, maskedIdentifier);
    }

    /** 是否可以直接签发 JWT（userId 有效）。NEED_BIND 时严禁调用签发逻辑。 */
    public boolean canIssueToken() {
        return status == Status.LOGIN || status == Status.REGISTERED_FULL;
    }

    public enum Status {
        /** 老用户，主凭证已存在，直接登录。 */
        LOGIN,
        /** 全新用户，主凭证 + 所有 hints 原子落地。 */
        REGISTERED_FULL,
        /** 信任域冲突：主凭证为新，但 EXTERNAL hint 与系统已有 OWN 凭证碰撞，需 OTP 二次验证。 */
        NEED_BIND
    }
}