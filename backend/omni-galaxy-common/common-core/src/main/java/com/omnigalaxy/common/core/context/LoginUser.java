package com.omnigalaxy.common.core.context;

import java.util.Set;

/**
 * 当前请求线程中的登录用户快照（不可变值对象）。
 *
 * <p>由网关在路由时从 JWT 中解析，通过 X-User-Id 与 X-User-Roles Header 透传给下游微服务，
 * 由 SecurityInterceptor 在请求入口处构建一次后存入 {@link UserContext}，全链路只读。
 */
public record LoginUser(Long userId, Set<String> roles) {

    public LoginUser {
        roles = (roles == null) ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(String... required) {
        for (String r : required) {
            if (roles.contains(r)) return true;
        }
        return false;
    }

    public boolean hasAllRoles(String... required) {
        for (String r : required) {
            if (!roles.contains(r)) return false;
        }
        return true;
    }
}
