package com.omnigalaxy.common.mybatis.util;

import lombok.extern.slf4j.Slf4j;

/**
 * 当前登录用户 ID 的线程级持有者。
 *
 * <p>职责边界：common-mybatis 只定义存取契约，不关心 ID 从哪来。
 * 调用方（common-security 的认证过滤器）在请求进入时调用 {@link #set}，
 * 在请求结束时务必调用 {@link #clear} 防止线程池复用导致的数据污染。
 */
@Slf4j
public final class CurrentUserHolder {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {}

    public static void set(Long userId) {
        log.debug(">>>> [common-mybatis] 线程暂存用户 ID: {}", userId);
        HOLDER.set(userId);
    }

    public static Long getUserId() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
        log.debug("<<<< [common-mybatis] 线程用户上下文清理完成");
    }
}
