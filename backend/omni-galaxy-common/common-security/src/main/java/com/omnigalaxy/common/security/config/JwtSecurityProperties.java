package com.omnigalaxy.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 校验侧（解析方）配置。
 *
 * <p>{@code secret} 必须与签发方（platform-auth-biz 的 JwtProperties）
 * 配置同一个 {@code omni-galaxy.jwt.secret}（即同一份 JWT_SECRET 环境变量），
 * 否则签名校验必然失败。
 */
@Data
@ConfigurationProperties(prefix = "omni-galaxy.jwt")
public class JwtSecurityProperties {

    /** Base64 编码的 HMAC-SHA256 密钥，与签发方共用同一份 JWT_SECRET */
    private String secret;
}