package com.omnigalaxy.platform.auth.component;

import com.omnigalaxy.platform.auth.api.dto.LoginResponse;
import com.omnigalaxy.platform.auth.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 平台登录凭证签发器。
 *
 * <p>统一收口"userId → LoginResponse"的签发链路，确保角色列表、有效期等签发策略
 * 只在此处维护，上游服务无需感知 JWT 细节。
 */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtUtils jwtUtils;

    /**
     * 为指定用户签发平台登录凭证。
     *
     * @param userId 已通过身份认证的用户 ID
     * @return 包含 token、有效期与 userId 的登录响应体
     */
    public LoginResponse issue(Long userId) {
        return new LoginResponse(
                jwtUtils.generateToken(userId, List.of("ROLE_USER")),
                jwtUtils.getExpireSeconds(),
                userId
        );
    }
}