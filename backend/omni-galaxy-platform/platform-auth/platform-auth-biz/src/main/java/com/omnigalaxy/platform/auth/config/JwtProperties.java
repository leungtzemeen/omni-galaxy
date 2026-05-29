package com.omnigalaxy.platform.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "omni-galaxy.jwt")
public class JwtProperties {

    /** Base64 编码的 HMAC-SHA256 密钥，生产环境通过 JWT_SECRET 环境变量注入 */
    private String secret;

    /** 令牌有效期（毫秒），默认 24 小时 */
    private long expireMs = 86_400_000L;
}
