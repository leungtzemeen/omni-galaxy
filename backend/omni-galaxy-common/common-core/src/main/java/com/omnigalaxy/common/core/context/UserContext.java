package com.omnigalaxy.common.core.context;

import lombok.extern.slf4j.Slf4j;

/**
 * 当前请求线程的用户上下文容器。
 *
 * <p>职责边界：common-core 只定义存取契约，不关心 LoginUser 从哪来。
 * SecurityInterceptor（common-security）在请求入口处调用 {@link #set}，
 * 在 afterCompletion 时务必调用 {@link #clear}，防止虚拟线程池复用导致的上下文污染。
 */
@Slf4j
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {}

    public static void set(LoginUser user) {
        log.debug(">>>> [common-core] 线程暂存用户上下文 userId: {}", user.userId());
        HOLDER.set(user);
    }

    public static LoginUser getLoginUser() {
        return HOLDER.get();
    }

    /** 便捷方法，持久层自动填充（common-mybatis）的高频调用点。 */
    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user != null ? user.userId() : null;
    }

    public static void clear() {
        HOLDER.remove();
        log.debug("<<<< [common-core] 线程用户上下文清理完成");
    }
}
